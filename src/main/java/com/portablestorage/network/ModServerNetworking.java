package com.portablestorage.network;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.screen.CraftingWarehouseScreenHandler;
import com.portablestorage.util.WarehouseSetting;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端网络处理器
 * 处理客户端发送到服务端的网络数据包
 */
public class ModServerNetworking {

    public static void handleOpenCrafting(OpenCraftingPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.hasWorkbenchUpgrade())
                return;

            player.openMenu(new SimpleMenuProvider(
                    (syncId, inventory, p) -> new CraftingWarehouseScreenHandler(syncId, inventory,
                            ContainerLevelAccess.create(player.level(), player.blockPosition())),
                    Component.translatable("container.crafting")));
        });
    }

    public static void handleUpdateWarehouseState(C2SUpdateWarehouseStatePayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);

            payload.scrollDelta().ifPresent(delta -> warehouse.setScrollOffset(warehouse.getScrollOffset() - delta));
            payload.searchText().ifPresent(warehouse::setSearchText);
            payload.rowsDelta().ifPresent(delta -> warehouse.setVisibleRows(warehouse.getVisibleRows() + delta));
            payload.upgradeScrollDelta()
                    .ifPresent(delta -> warehouse.setUpgradeScrollOffset(warehouse.getUpgradeScrollOffset() - delta));

            if (payload.settingId().isPresent() && payload.settingValue().isPresent()) {
                int value = payload.settingValue().get();
                WarehouseSetting setting = WarehouseSetting.values()[payload.settingId().get()];
                switch (setting) {
                    case FOLD -> warehouse.setFolded(value == 1);
                    case SORT_MODE -> warehouse.setSortMode(value);
                    case SORT_ORDER -> warehouse.setAscending(value == 1);
                    case QUICK_INTERACTION -> warehouse.setQuickInteraction(value == 1);
                    case SMART_COLLAPSE -> warehouse.setSmartCollapse(value == 1);
                    case CRAFT_REFILL -> warehouse.setCraftRefill(value == 1);
                }
            }

            syncChanges(player);
        });
    }

    public static void handleUpgradeInteraction(C2SUpgradeInteractionPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            com.portablestorage.upgrade.UpgradeType type = com.portablestorage.upgrade.UpgradeRegistry
                    .get(payload.upgradeId());

            // 校验：仓库启用、升级类型存在、且该升级确实已在仓库中安装物品
            if (type != null && warehouse.isEnabled() && !warehouse.getUpgrade(payload.upgradeId()).isEmpty()) {
                if (payload.button() == 1) { // 右键
                    type.onRightClick(warehouse, player);
                } else if (payload.button() == 2) { // 中键
                    type.onMiddleClick(warehouse, player);
                }
                syncChanges(player);
            }
        });
    }

    public static void handleQuickTransfer(QuickTransferPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();

            int slotId = payload.slotId();
            if (slotId < 0 || slotId >= player.containerMenu.slots.size())
                return;

            Slot slot = player.containerMenu.slots.get(slotId);
            if (slot.container instanceof PlayerWarehouse warehouse) {
                // 提前验证：仓库必须启用且快速交互开启
                if (!warehouse.isEnabled() || !warehouse.isQuickInteraction() || warehouse.isFolded()) {
                    return;
                }
                // 验证槽位中确实有物品
                ItemStack stackInSlot = warehouse.getItem(slot.getContainerSlot());
                if (stackInSlot.isEmpty()) {
                    return;
                }
                WarehouseManager.tryTransferToInventory(warehouse, slot.getContainerSlot(), player);
                syncChanges(player);
            } else if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                PlayerWarehouse warehouse = getWarehouse(player);
                // 提前验证：仓库必须启用且快速交互开启
                if (!warehouse.isEnabled() || !warehouse.isQuickInteraction() || warehouse.isFolded()) {
                    return;
                }
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    // 使用 addFluid 以支持流体桶的自动分离
                    ItemStack remaining = WarehouseManager.addFluid(warehouse, stack, player);
                    slot.set(remaining);
                    syncChanges(player);
                }
            }
        });
    }

    public static void handleUpdateServerConfig(UpdateServerConfigPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            if (!ModConfig.allowHotReload)
                return;

            ServerPlayer player = context.player();
            // 权限验证：仅允许 4 级 OP 修改服务端配置
            if (!context.server().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) {
                return;
            }

            // 更新服务端配置
            ModConfig.enable3x3Crafting = payload.enable3x3Crafting();
            ModConfig.dropStorageOnDeath = payload.dropStorageOnDeath();
            ModConfig.maxStorageTypes = payload.maxStorageTypes();
            ModConfig.maxItemStackSize = payload.maxItemStackSize();
            ModConfig.baseMaxStorageTypes = payload.baseMaxStorageTypes();
            ModConfig.baseMaxItemStackSize = payload.baseMaxItemStackSize();
            ModConfig.maxItemNbtSize = payload.maxItemNbtSize();
            ModConfig.unconditionalWarehouse = payload.unconditionalWarehouse();
            ModConfig.baseWarehouseActivationItem = payload.baseWarehouseActivationItem();
            ModConfig.fullWarehouseActivationItem = payload.fullWarehouseActivationItem();
            ModConfig.hopperRange = payload.hopperRange();
            ModConfig.hopperFrequency = payload.hopperFrequency();
            ModConfig.lavaInfiniteThreshold = payload.lavaInfiniteThreshold();
            ModConfig.waterInfiniteThreshold = payload.waterInfiniteThreshold();
            ModConfig.riftUpgradeItem = payload.riftUpgradeItem();
            ModConfig.riftChunkSize = payload.riftChunkSize();
            ModConfig.enableRiftForcedLoading = payload.enableRiftForcedLoading();
            ModConfig.riftForcedLoadingRange = payload.riftForcedLoadingRange();
            ModConfig.enableConduitUpgrade = payload.enableConduitUpgrade();

            // 保存到文件
            ModConfig.save();

            // 更新在线玩家的强制加载状态
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                var warehouse = getWarehouse(p);
                com.portablestorage.world.SpaceRiftManager.updatePlotForcedLoading(p, warehouse, true);
            }

            // 同步给所有玩家
            SyncConfigPayload sync = new SyncConfigPayload(
                    ModConfig.enable3x3Crafting,
                    ModConfig.dropStorageOnDeath,
                    ModConfig.allowHotReload,
                    ModConfig.maxStorageTypes,
                    ModConfig.maxItemStackSize,
                    ModConfig.baseMaxStorageTypes,
                    ModConfig.baseMaxItemStackSize,
                    ModConfig.maxItemNbtSize,
                    ModConfig.unconditionalWarehouse,
                    ModConfig.baseWarehouseActivationItem,
                    ModConfig.fullWarehouseActivationItem,
                    ModConfig.hopperRange,
                    ModConfig.hopperFrequency,
                    ModConfig.lavaInfiniteThreshold,
                    ModConfig.waterInfiniteThreshold,
                    ModConfig.riftUpgradeItem,
                    ModConfig.riftChunkSize,
                    ModConfig.enableRiftForcedLoading,
                    ModConfig.riftForcedLoadingRange,
                    ModConfig.enableConduitUpgrade);

            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, sync);
            }
        });
    }

    public static void handleQueryConfigPermission(C2SQueryConfigPermissionPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            boolean canEdit = ModConfig.allowHotReload
                    && context.server().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
            ServerPlayNetworking.send(player, new S2CConfigPermissionResultPayload(canEdit));
        });
    }

    public static void handleRefill(RefillPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled() || !warehouse.isCraftRefill())
                return;

            net.minecraft.world.inventory.AbstractContainerMenu handler = player.containerMenu;

            ItemStack template = payload.targetStack();
            if (template.isEmpty() || payload.slotIds().isEmpty())
                return;

            // 获取所有需要补货的槽位（来自客户端的指定）
            java.util.List<Integer> targetIndices = new java.util.ArrayList<>();
            for (int idx : payload.slotIds()) {
                if (idx >= 0 && idx < handler.slots.size()) {
                    ItemStack stack = handler.getSlot(idx).getItem();
                    // 确保槽位物品匹配模板（或者是空的）
                    if (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, template)) {
                        targetIndices.add(idx);
                    }
                }
            }

            if (targetIndices.isEmpty())
                return;

            // 计算总共需要的数量
            int maxStackSize = Math.min(template.getMaxStackSize(), player.getInventory().getMaxStackSize());
            int totalNeed = 0;
            int[] needs = new int[targetIndices.size()];

            for (int i = 0; i < targetIndices.size(); i++) {
                ItemStack stack = handler.getSlot(targetIndices.get(i)).getItem();
                int currentCount = stack.isEmpty() ? 0 : stack.getCount();
                needs[i] = Math.max(0, maxStackSize - currentCount);
                totalNeed += needs[i];
            }

            if (totalNeed <= 0)
                return;

            // 从仓库取出物品（不强制匹配组件，兼容智能折叠）
            ItemStack taken = WarehouseManager.takeMatching(warehouse, template, totalNeed, false);
            if (taken.isEmpty())
                return;

            // 均分补给
            int remaining = taken.getCount();
            int[] distribution = new int[targetIndices.size()];

            while (remaining > 0) {
                boolean anyAdded = false;
                for (int i = 0; i < targetIndices.size() && remaining > 0; i++) {
                    if (distribution[i] < needs[i]) {
                        distribution[i]++;
                        remaining--;
                        anyAdded = true;
                    }
                }
                if (!anyAdded)
                    break;
            }

            // 应用分配结果
            for (int i = 0; i < targetIndices.size(); i++) {
                if (distribution[i] <= 0)
                    continue;
                net.minecraft.world.inventory.Slot slot = handler.getSlot(targetIndices.get(i));
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    slot.set(template.copyWithCount(distribution[i]));
                } else {
                    stack.grow(distribution[i]);
                    slot.setChanged();
                }
            }

            handler.broadcastChanges();
            syncChanges(player);
        });
    }

    public static void handleUpdateHopperFilters(C2SUpdateHopperFiltersPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PlayerWarehouse warehouse = getWarehouse(context.player());
            warehouse.setHopperFilters(payload.filters(), payload.blacklist());
        });
    }

    public static void handleUpdateFoodFilters(C2SUpdateFoodFiltersPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PlayerWarehouse warehouse = getWarehouse(context.player());
            warehouse.setFoodFilters(payload.filters(), payload.blacklist());
        });
    }

    public static void handleUpdateForbiddenPlayers(C2SUpdateForbiddenPlayersPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PlayerWarehouse warehouse = getWarehouse(context.player());
            warehouse.setForbidden(payload.playerUuid(), payload.forbidden());
            syncChanges(context.player());
        });
    }

    public static void handleDropWarehouseItem(C2SDropWarehouseItemPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled())
                return;

            int slotId = payload.slotId();
            if (slotId < 0 || slotId >= player.containerMenu.slots.size())
                return;

            Slot slot = player.containerMenu.slots.get(slotId);
            if (slot.container instanceof PlayerWarehouse) {
                ItemStack stackInSlot = slot.getItem();
                if (!stackInSlot.isEmpty()) {
                    int toTake = payload.dropFullStack()
                            ? (int) Math.min(stackInSlot.getMaxStackSize(),
                                    warehouse.getRealCount(slot.getContainerSlot()))
                            : 1;
                    ItemStack dropped = WarehouseManager.removeItem(warehouse, slot.getContainerSlot(), toTake, true);
                    if (!dropped.isEmpty()) {
                        player.drop(dropped, true);
                        syncChanges(player);
                    }
                }
            }
        });
    }

    public static void handleUpdateFrozenState(C2SUpdateFrozenStatePayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PlayerWarehouse warehouse = getWarehouse(context.player());
            warehouse.setFrozen(payload.frozen());
            syncChanges(context.player());
        });
    }

    public static void handleDoubleClickQuickStore(C2SDoubleClickQuickStorePayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);

            // 验证：仓库必须启用、快速交互开启且未折叠
            if (!warehouse.isEnabled() || !warehouse.isQuickInteraction() || warehouse.isFolded()) {
                return;
            }

            // 获取光标中的物品
            ItemStack cursorStack = player.containerMenu.getCarried();
            if (cursorStack.isEmpty()) {
                return;
            }

            // 查找背包中所有相同物品并存入仓库
            boolean storedAny = false;
            for (int i = 0; i < player.containerMenu.slots.size(); i++) {
                net.minecraft.world.inventory.Slot slot = player.containerMenu.slots.get(i);
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                    int containerSlot = slot.getContainerSlot();
                    // 仅处理主背包和快捷栏（0-35）
                    if (containerSlot >= 0 && containerSlot < 36) {
                        ItemStack stack = slot.getItem();
                        if (!stack.isEmpty()
                                && net.minecraft.world.item.ItemStack.isSameItemSameComponents(cursorStack, stack)) {
                            // 使用 addFluid 以支持流体桶的自动分离
                            ItemStack remaining = com.portablestorage.logic.WarehouseManager.addFluid(warehouse, stack,
                                    player);
                            slot.set(remaining);
                            if (remaining.getCount() < stack.getCount()) {
                                storedAny = true;
                            }
                        }
                    }
                }
            }

            if (storedAny) {
                syncChanges(player);
            }
        });
    }

    public static void handleTogglePinned(C2STogglePinnedPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            warehouse.togglePinned(payload.slotId());
            syncChanges(player);
            int sortedIndex = payload.slotId() + warehouse.getScrollOffset() * 9;
            var sorted = warehouse.getSortedEntries();
            if (sortedIndex >= 0 && sortedIndex < sorted.size()) {
                boolean pinned = sorted.get(sortedIndex).isPinned();
                ServerPlayNetworking.send(player, new S2CWarehousePinnedUpdatePayload(sortedIndex, pinned));
            }
        });
    }

    public static void handleRecipeTransfer(C2SRecipeTransferPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;

            boolean isWarehouseCrafting = menu instanceof CraftingWarehouseScreenHandler;
            boolean isVanillaInventory = menu instanceof net.minecraft.world.inventory.InventoryMenu;

            if (!isWarehouseCrafting && !isVanillaInventory)
                return;

            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(payload.recipeId());
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = net.minecraft.resources.ResourceKey
                    .create(net.minecraft.core.registries.Registries.RECIPE, id);
            net.minecraft.world.item.crafting.RecipeHolder<?> recipeHolder = ((ServerLevel) player.level())
                    .getServer()
                    .getRecipeManager()
                    .byKey(key)
                    .orElse(null);
            if (recipeHolder == null
                    || !(recipeHolder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe))
                return;

            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled())
                return;

            // 动态判断当前格网大小
            boolean is3x3 = isWarehouseCrafting
                    || (isVanillaInventory && com.portablestorage.util.WarehouseUtils.is3x3Enabled(player));
            int gridWidth = is3x3 ? 3 : 2;
            int gridHeight = is3x3 ? 3 : 2;
            int containerStride = 3; // 无论合成界面还是背包，底层都是 3x3 容器

            // 清空当前合成槽位到仓库
            for (Slot slot : menu.slots) {
                if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer
                        && !(slot instanceof net.minecraft.world.inventory.ResultSlot)) {
                    if (slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        WarehouseManager.addItem(warehouse, stack, player);
                        slot.set(stack);
                    }
                }
            }

            // 获取配料表和形状（通过 PlacementInfo 提供的 ingredients）
            java.util.List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.placementInfo()
                    .ingredients();
            int recipeWidth = gridWidth;
            int recipeHeight = gridHeight;

            if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                recipeWidth = shaped.getWidth();
                recipeHeight = shaped.getHeight();
            } else {
                // 无序配方
                recipeWidth = (ingredients.size() <= 4 && !is3x3) ? 2 : 3;
                recipeHeight = (int) Math.ceil((double) ingredients.size() / recipeWidth);
            }

            // 校验配方是否放得下
            if (recipeWidth > gridWidth || recipeHeight > gridHeight)
                return;

            // 尝试从仓库和玩家背包获取物品填充
            for (int r = 0; r < recipeHeight; r++) {
                for (int c = 0; c < recipeWidth; c++) {
                    int ingredientIdx = r * recipeWidth + c;
                    if (ingredientIdx >= ingredients.size())
                        continue;

                    net.minecraft.world.item.crafting.Ingredient ingredient = ingredients.get(ingredientIdx);
                    if (ingredient == null || ingredient.isEmpty())
                        continue;

                    ItemStack found = ItemStack.EMPTY;

                    // 优先从仓库查找：直接按 Ingredient.test 匹配，避免使用已废弃的 Ingredient.items()
                    ItemStack taken = WarehouseManager.takeMatchingIngredient(warehouse, ingredient, 1);
                    if (!taken.isEmpty()) {
                        found = taken;
                    }

                    // 如果仓库没有，从玩家背包查找
                    if (found.isEmpty()) {
                        for (int invIdx = 0; invIdx < player.getInventory().getContainerSize(); invIdx++) {
                            ItemStack invStack = player.getInventory().getItem(invIdx);
                            if (ingredient.test(invStack)) {
                                found = player.getInventory().removeItem(invIdx, 1);
                                break;
                            }
                        }
                    }

                    if (!found.isEmpty()) {
                        int targetContainerSlot = r * containerStride + c;
                        for (Slot slot : menu.slots) {
                            if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer &&
                                    !(slot instanceof net.minecraft.world.inventory.ResultSlot) &&
                                    slot.getContainerSlot() == targetContainerSlot) {
                                slot.set(found);
                                break;
                            }
                        }
                    }
                }
            }

            menu.broadcastChanges();
            syncChanges(player);
        });
    }

    private static PlayerWarehouse getWarehouse(ServerPlayer player) {
        return ModComponents.get(player).getWarehouse(player.getUUID());
    }

    private static void syncChanges(ServerPlayer player) {
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }
}
