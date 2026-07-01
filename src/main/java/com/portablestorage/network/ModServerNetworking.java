package com.portablestorage.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.portablestorage.storage.service.WarehouseService;
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
            boolean changed = false;

            changed |= payload.scrollDelta()
                    .map(delta -> applyScrollDelta(warehouse, delta))
                    .orElse(false);
            changed |= payload.searchText()
                    .map(text -> applySearchText(warehouse, text))
                    .orElse(false);
            changed |= payload.rowsDelta()
                    .map(delta -> applyRowsDelta(warehouse, delta))
                    .orElse(false);
            changed |= payload.upgradeScrollDelta()
                    .map(delta -> applyUpgradeScrollDelta(warehouse, delta))
                    .orElse(false);

            if (payload.settingId().isPresent() && payload.settingValue().isPresent()) {
                changed |= applySetting(warehouse, payload.settingId().get(), payload.settingValue().get());
            }

            if (changed) {
                syncChanges(player);
            }
        });
    }

    private static boolean applyScrollDelta(PlayerWarehouse warehouse, int delta) {
        int before = warehouse.getScrollOffset();
        warehouse.setScrollOffset(before - delta);
        return warehouse.getScrollOffset() != before;
    }

    private static boolean applySearchText(PlayerWarehouse warehouse, String text) {
        String normalized = text.toLowerCase();
        if (warehouse.getSearchText().equals(normalized)) {
            return false;
        }
        warehouse.setSearchText(text);
        return true;
    }

    private static boolean applyRowsDelta(PlayerWarehouse warehouse, int delta) {
        int before = warehouse.getVisibleRows();
        int target = Math.clamp(before + delta, 1, 12);
        if (target == before) {
            return false;
        }
        warehouse.setVisibleRows(before + delta);
        return true;
    }

    private static boolean applyUpgradeScrollDelta(PlayerWarehouse warehouse, int delta) {
        int before = warehouse.getUpgradeScrollOffset();
        warehouse.setUpgradeScrollOffset(before - delta);
        return warehouse.getUpgradeScrollOffset() != before;
    }

    private static boolean applySetting(PlayerWarehouse warehouse, int settingId, int value) {
        WarehouseSetting setting = WarehouseSetting.values()[settingId];
        switch (setting) {
            case FOLD -> {
                boolean before = warehouse.isFolded();
                if (before == (value == 1)) {
                    return false;
                }
                warehouse.setFolded(value == 1);
                return warehouse.isFolded() != before;
            }
            case SORT_MODE -> {
                if (warehouse.getSortMode() == value) {
                    return false;
                }
                warehouse.setSortMode(value);
                return true;
            }
            case SORT_ORDER -> {
                if (warehouse.isAscending() == (value == 1)) {
                    return false;
                }
                warehouse.setAscending(value == 1);
                return true;
            }
            case QUICK_INTERACTION -> {
                if (warehouse.isQuickInteraction() == (value == 1)) {
                    return false;
                }
                warehouse.setQuickInteraction(value == 1);
                return true;
            }
            case SMART_COLLAPSE -> {
                if (warehouse.isSmartCollapse() == (value == 1)) {
                    return false;
                }
                warehouse.setSmartCollapse(value == 1);
                return true;
            }
            case CRAFT_REFILL -> {
                if (warehouse.isCraftRefill() == (value == 1)) {
                    return false;
                }
                warehouse.setCraftRefill(value == 1);
                return true;
            }
        }
        return false;
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
                WarehouseService.commitIfWarehouseChanged(player, warehouse, "upgrade_interaction", () -> {
                    if (payload.button() == 1) { // 右键
                        type.onRightClick(warehouse, player);
                    } else if (payload.button() == 2) { // 中键
                        type.onMiddleClick(warehouse, player);
                    }
                    return null;
                });
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
                WarehouseService.commitIfWarehouseChanged(player, warehouse, "quick_transfer.from_warehouse", () -> {
                    WarehouseManager.tryTransferToInventory(warehouse, slot.getContainerSlot(), player);
                    return null;
                });
            } else if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                PlayerWarehouse warehouse = getWarehouse(player);
                // 提前验证：仓库必须启用且快速交互开启
                if (!warehouse.isEnabled() || !warehouse.isQuickInteraction() || warehouse.isFolded()) {
                    return;
                }
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    WarehouseService.commitIfWarehouseChanged(player, warehouse, "quick_transfer.to_warehouse", () -> {
                        ItemStack remaining = WarehouseManager.addFluid(warehouse, stack, player);
                        slot.set(remaining);
                        return null;
                    });
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
            ModConfig.riftPlotSpacingChunks = payload.riftPlotSpacingChunks();
            ModConfig.riftFloorY = payload.riftFloorY();
            ModConfig.enableRiftForcedLoading = payload.enableRiftForcedLoading();
            ModConfig.riftForcedLoadingRange = payload.riftForcedLoadingRange();
            ModConfig.enableRiftAvatar = payload.enableRiftAvatar();
            ModConfig.enableRiftBorder = payload.enableRiftBorder();
            ModConfig.riftBorderWarningBlocks = payload.riftBorderWarningBlocks();
            ModConfig.enableConduitUpgrade = payload.enableConduitUpgrade();

            // 保存到文件
            ModConfig.save();

            // 更新在线玩家的强制加载状态，并让裂隙内玩家重新接收个人边界
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                var warehouse = getWarehouse(p);
                com.portablestorage.world.SpaceRiftManager.updatePlotForcedLoading(p, warehouse, true);
                if (p.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                    warehouse.setRiftBorderResendTicks(40);
                }
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
                    ModConfig.riftPlotSpacingChunks,
                    ModConfig.riftFloorY,
                    ModConfig.enableRiftForcedLoading,
                    ModConfig.riftForcedLoadingRange,
                    ModConfig.enableRiftAvatar,
                    ModConfig.enableRiftBorder,
                    ModConfig.riftBorderWarningBlocks,
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
            if (!isRefillMenu(handler)) {
                return;
            }

            ItemStack template = payload.targetStack();
            if (template.isEmpty() || payload.slotIds().isEmpty())
                return;

            java.util.List<Slot> targetSlots = new java.util.ArrayList<>();
            for (int requestedSlot : payload.slotIds()) {
                Slot slot = resolveRefillSlot(handler, requestedSlot);
                if (slot == null || targetSlots.contains(slot)) {
                    continue;
                }
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    if (slot.mayPlace(template)) {
                        targetSlots.add(slot);
                    }
                } else if (ItemStack.isSameItemSameComponents(stack, template)) {
                    targetSlots.add(slot);
                }
            }

            if (targetSlots.isEmpty())
                return;

            int totalNeed = 0;
            int[] needs = new int[targetSlots.size()];

            for (int i = 0; i < targetSlots.size(); i++) {
                Slot slot = targetSlots.get(i);
                ItemStack stack = slot.getItem();
                int currentCount = stack.isEmpty() ? 0 : stack.getCount();
                int maxStackSize = Math.min(template.getMaxStackSize(), slot.getMaxStackSize());
                needs[i] = Math.max(0, maxStackSize - currentCount);
                totalNeed += needs[i];
            }

            if (totalNeed <= 0)
                return;

            final int requestedTotalNeed = totalNeed;
            java.util.Map<Slot, ItemStack> slotSnapshot = snapshotSlots(targetSlots);
            boolean changed;
            try {
                changed = WarehouseService.transaction(player, warehouse, "craft_refill", tx -> {
                    ItemStack taken = WarehouseManager.takeMatching(warehouse, template, requestedTotalNeed, true);
                    if (taken.isEmpty())
                        return false;

                    int remaining = taken.getCount();
                    int[] distribution = new int[targetSlots.size()];

                    while (remaining > 0) {
                        boolean anyAdded = false;
                        for (int i = 0; i < targetSlots.size() && remaining > 0; i++) {
                            if (distribution[i] < needs[i]) {
                                distribution[i]++;
                                remaining--;
                                anyAdded = true;
                            }
                        }
                        if (!anyAdded)
                            break;
                    }

                    boolean appliedAny = false;
                    for (int i = 0; i < targetSlots.size(); i++) {
                        if (distribution[i] <= 0)
                            continue;
                        Slot slot = targetSlots.get(i);
                        ItemStack stack = slot.getItem();
                        if (stack.isEmpty()) {
                            slot.set(taken.copyWithCount(distribution[i]));
                        } else {
                            stack.grow(distribution[i]);
                            slot.setChanged();
                        }
                        appliedAny = true;
                    }

                    if (!appliedAny) {
                        return false;
                    }
                    tx.commit();
                    handler.broadcastChanges();
                    return true;
                });
            } catch (RuntimeException | Error throwable) {
                restoreSlots(slotSnapshot);
                throw throwable;
            }
            if (!changed) {
                restoreSlots(slotSnapshot);
            }
        });
    }

    private static boolean isRefillMenu(net.minecraft.world.inventory.AbstractContainerMenu handler) {
        return handler instanceof net.minecraft.world.inventory.AbstractCraftingMenu
                || handler instanceof CraftingWarehouseScreenHandler;
    }

    private static Slot resolveRefillSlot(net.minecraft.world.inventory.AbstractContainerMenu handler, int requestedSlot) {
        if (requestedSlot >= 0 && requestedSlot < handler.slots.size()) {
            Slot slot = handler.getSlot(requestedSlot);
            if (isRefillCraftingSlot(slot)) {
                return slot;
            }
        }
        for (Slot slot : handler.slots) {
            if (isRefillCraftingSlot(slot) && slot.getContainerSlot() == requestedSlot) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isRefillCraftingSlot(Slot slot) {
        return slot != null
                && slot.container instanceof net.minecraft.world.inventory.CraftingContainer
                && !(slot instanceof net.minecraft.world.inventory.ResultSlot);
    }

    private static List<Slot> craftingSlots(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (isCraftingInputSlot(slot)) {
                result.add(slot);
            }
        }
        return result;
    }

    private static Slot findCraftingSlot(net.minecraft.world.inventory.AbstractContainerMenu menu, int containerSlot) {
        for (Slot slot : menu.slots) {
            if (isCraftingInputSlot(slot) && slot.getContainerSlot() == containerSlot) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isCraftingInputSlot(Slot slot) {
        return slot != null
                && slot.container instanceof net.minecraft.world.inventory.CraftingContainer
                && !(slot instanceof net.minecraft.world.inventory.ResultSlot);
    }

    private static ItemStack takeRecipeIngredient(PlayerWarehouse warehouse, ServerPlayer player,
            net.minecraft.world.item.crafting.Ingredient ingredient) {
        ItemStack taken = WarehouseManager.takeMatchingIngredient(warehouse, ingredient, 1);
        if (!taken.isEmpty()) {
            return taken;
        }

        int inventorySize = Math.min(36, player.getInventory().getContainerSize());
        for (int invIdx = 0; invIdx < inventorySize; invIdx++) {
            ItemStack invStack = player.getInventory().getItem(invIdx);
            if (ingredient.test(invStack)) {
                return player.getInventory().removeItem(invIdx, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean canPlaceToolInSlot(PlayerWarehouse warehouse, ItemStack stack, int slot, ServerPlayer player) {
        if (stack.isEmpty() || stack.getCount() != 1 || !isToolWarehouseItem(stack)) {
            return false;
        }
        if (!WarehouseManager.canStoreItem(warehouse, stack, player, "quick_tool_swap.hand")) {
            return false;
        }
        if (!warehouse.getToolSlotStack(slot).isEmpty()) {
            return true;
        }
        int typeLimit = warehouse.getMaxStorageTypes();
        return typeLimit < 0 || warehouse.getStoredItemTypeCount() < typeLimit;
    }

    private static boolean isToolWarehouseItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.has(net.minecraft.core.component.DataComponents.TOOL)
                        || stack.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE));
    }

    private static java.util.Map<Slot, ItemStack> snapshotSlots(List<Slot> slots) {
        java.util.Map<Slot, ItemStack> snapshot = new LinkedHashMap<>();
        for (Slot slot : slots) {
            snapshot.put(slot, slot.getItem().copy());
        }
        return snapshot;
    }

    private static void restoreSlots(java.util.Map<Slot, ItemStack> snapshot) {
        for (Map.Entry<Slot, ItemStack> entry : snapshot.entrySet()) {
            entry.getKey().set(entry.getValue().copy());
        }
    }

    private static ExternalStateSnapshot snapshotExternalState(ServerPlayer player,
            net.minecraft.world.inventory.AbstractContainerMenu menu, List<Slot> slots) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory.add(player.getInventory().getItem(i).copy());
        }
        return new ExternalStateSnapshot(player, menu, snapshotSlots(slots), inventory, menu.getCarried().copy());
    }

    private static void restoreExternalState(ExternalStateSnapshot snapshot) {
        restoreSlots(snapshot.slots());
        int inventorySize = Math.min(snapshot.inventory().size(), snapshot.player().getInventory().getContainerSize());
        for (int i = 0; i < inventorySize; i++) {
            snapshot.player().getInventory().setItem(i, snapshot.inventory().get(i).copy());
        }
        snapshot.player().getInventory().setChanged();
        snapshot.menu().setCarried(snapshot.carried().copy());
        snapshot.menu().broadcastChanges();
    }

    private record ExternalStateSnapshot(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu,
            java.util.Map<Slot, ItemStack> slots, List<ItemStack> inventory, ItemStack carried) {
    }

    public static void handleUpdateHopperFilters(C2SUpdateHopperFiltersPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "update_hopper_filters", () -> {
                warehouse.setHopperFilters(payload.filters(), payload.blacklist());
                return null;
            });
        });
    }

    public static void handleUpdateFoodFilters(C2SUpdateFoodFiltersPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "update_food_filters", () -> {
                warehouse.setFoodFilters(payload.filters(), payload.blacklist());
                return null;
            });
        });
    }

    public static void handleUpdateForbiddenPlayers(C2SUpdateForbiddenPlayersPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "update_forbidden_players", () -> {
                warehouse.setForbidden(payload.playerUuid(), payload.forbidden());
                return null;
            });
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
                    if (WarehouseManager.isVirtualFluid(stackInSlot.getItem())) {
                        return;
                    }
                    int toTake = payload.dropFullStack()
                            ? (int) Math.min(stackInSlot.getMaxStackSize(),
                                    warehouse.getRealCount(slot.getContainerSlot()))
                            : 1;
                    ItemStack dropped = WarehouseService.commitIfWarehouseChanged(player, warehouse,
                            "drop_warehouse_item", () -> WarehouseManager.removeItem(warehouse, slot.getContainerSlot(), toTake, true));
                    if (!dropped.isEmpty()) {
                        player.drop(dropped, true);
                    }
                }
            }
        });
    }

    public static void handleUpdateFrozenState(C2SUpdateFrozenStatePayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PlayerWarehouse warehouse = getWarehouse(context.player());
            if (warehouse.isFrozen() == payload.frozen()) {
                return;
            }
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

            boolean storedAny = WarehouseService.commitIfWarehouseChanged(player, warehouse,
                    "double_click_quick_store", () -> {
                        boolean changed = false;
                        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
                            net.minecraft.world.inventory.Slot slot = player.containerMenu.slots.get(i);
                            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                                int containerSlot = slot.getContainerSlot();
                                if (containerSlot >= 0 && containerSlot < 36) {
                                    ItemStack stack = slot.getItem();
                                    if (!stack.isEmpty()
                                            && net.minecraft.world.item.ItemStack.isSameItemSameComponents(cursorStack,
                                                    stack)) {
                                        int beforeCount = stack.getCount();
                                        ItemStack remaining = WarehouseManager.addFluid(warehouse, stack, player);
                                        slot.set(remaining);
                                        if (remaining.getCount() < beforeCount) {
                                            changed = true;
                                        }
                                    }
                                }
                            }
                        }
                        return changed;
                    });

            if (storedAny && player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
        });
    }

    public static void handleTogglePinned(C2STogglePinnedPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "toggle_pinned", () -> {
                warehouse.togglePinned(payload.slotId());
                return null;
            });
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

            // JEI 可能发送 ResourceKey 风格 "ResourceKey[registry / namespace:path]" 或 "registry
            // / namespace:path"，需只取 namespace:path 并去掉末尾 ]
            String recipeIdRaw = payload.recipeId();
            String recipeIdStr = recipeIdRaw.contains(" / ") ? recipeIdRaw.substring(recipeIdRaw.indexOf(" / ") + 3)
                    : recipeIdRaw;
            if (recipeIdStr.endsWith("]")) {
                recipeIdStr = recipeIdStr.substring(0, recipeIdStr.length() - 1);
            }
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(recipeIdStr);
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

            boolean completed;
            ExternalStateSnapshot externalSnapshot = snapshotExternalState(player, menu, craftingSlots(menu));
            try {
                completed = WarehouseService.transaction(player, warehouse, "recipe_transfer", tx -> {
                    List<Slot> craftingSlots = craftingSlots(menu);
                    if (craftingSlots.isEmpty()) {
                        return false;
                    }

                    for (Slot slot : craftingSlots) {
                        ItemStack stack = slot.getItem();
                        if (stack.isEmpty()) {
                            continue;
                        }
                        ItemStack remaining = stack.copy();
                        WarehouseManager.addItem(warehouse, remaining, player);
                        if (!remaining.isEmpty()) {
                            return false;
                        }
                        slot.set(ItemStack.EMPTY);
                    }

                    var placement = recipe.placementInfo();
                    java.util.List<net.minecraft.world.item.crafting.Ingredient> ingredients = placement.ingredients();
                    int gridSize = gridWidth * gridHeight;

                    int recipeWidth = gridWidth;
                    int recipeHeight = gridHeight;
                    if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                        recipeWidth = shaped.getWidth();
                        recipeHeight = shaped.getHeight();
                    } else {
                        recipeWidth = (ingredients.size() <= 4 && !is3x3) ? 2 : 3;
                        recipeHeight = (int) Math.ceil((double) ingredients.size() / recipeWidth);
                    }
                    if (recipeWidth > gridWidth || recipeHeight > gridHeight) {
                        return false;
                    }

                    it.unimi.dsi.fastutil.ints.IntList slotToIngredientIndex = placement.slotsToIngredientIndex();

                    Map<Integer, ItemStack> plannedStacks = new LinkedHashMap<>();
                    for (int targetContainerSlot = 0; targetContainerSlot < slotToIngredientIndex.size()
                            && targetContainerSlot < gridSize; targetContainerSlot++) {
                        int ingredientIdx = slotToIngredientIndex.getInt(targetContainerSlot);
                        if (ingredientIdx == net.minecraft.world.item.crafting.PlacementInfo.EMPTY_SLOT
                                || ingredientIdx < 0 || ingredientIdx >= ingredients.size()) {
                            continue;
                        }

                        net.minecraft.world.item.crafting.Ingredient ingredient = ingredients.get(ingredientIdx);
                        if (ingredient == null || ingredient.isEmpty()) {
                            continue;
                        }

                        ItemStack found = takeRecipeIngredient(warehouse, player, ingredient);
                        if (found.isEmpty()) {
                            return false;
                        }
                        plannedStacks.put(targetContainerSlot, found);
                    }

                    for (Map.Entry<Integer, ItemStack> entry : plannedStacks.entrySet()) {
                        Slot targetSlot = findCraftingSlot(menu, entry.getKey());
                        if (targetSlot == null || !targetSlot.mayPlace(entry.getValue())) {
                            return false;
                        }
                        targetSlot.set(entry.getValue());
                    }

                    tx.commit();
                    menu.broadcastChanges();
                    return true;
                });
            } catch (RuntimeException | Error throwable) {
                restoreExternalState(externalSnapshot);
                throw throwable;
            }
            if (!completed) {
                restoreExternalState(externalSnapshot);
            }
        });
    }

    public static void handleRequestWarehouseSnapshot(C2SRequestWarehouseSnapshotPayload payload,
            ServerPlayNetworking.Context context) {
        context.server().execute(() -> WarehouseService.sync(context.player()));
    }

    public static void handleQuickToolSwap(C2SQuickToolSwapPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            int slot = payload.slot();
            if (!warehouse.isEnabled() || warehouse.getUpgrade(com.portablestorage.upgrade.ToolUpgrade.ID).isEmpty()) {
                return;
            }
            if (slot < 0 || slot >= com.portablestorage.storage.key.ToolWarehouseKey.SLOT_COUNT) {
                return;
            }

            ItemStack toolStack = warehouse.getToolSlotStack(slot);
            ItemStack handStack = player.getMainHandItem();
            if (toolStack.isEmpty()) {
                if (handStack.isEmpty() || !isToolWarehouseItem(handStack)) {
                    return;
                }
                if (!canPlaceToolInSlot(warehouse, handStack, slot, player)) {
                    return;
                }

                ItemStack handSnapshot = handStack.copy();
                boolean completed;
                try {
                    completed = WarehouseService.transaction(player, warehouse, "quick_tool_swap.store_hand", tx -> {
                        warehouse.setToolSlotStack(slot, handSnapshot.copyWithCount(1));
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        tx.commit();
                        if (player.containerMenu != null) {
                            player.containerMenu.broadcastChanges();
                        }
                        return true;
                    });
                } catch (RuntimeException | Error throwable) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
                    throw throwable;
                }
                if (!completed) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
                }
                return;
            }

            if (!handStack.isEmpty() && !isToolWarehouseItem(handStack)) {
                return;
            }
            if (!handStack.isEmpty() && !canPlaceToolInSlot(warehouse, handStack, slot, player)) {
                return;
            }

            ItemStack handSnapshot = handStack.copy();
            ItemStack toolSnapshot = toolStack.copy();
            boolean completed;
            try {
                completed = WarehouseService.transaction(player, warehouse, "quick_tool_swap.exchange", tx -> {
                    if (handSnapshot.isEmpty()) {
                        warehouse.setToolSlotStack(slot, ItemStack.EMPTY);
                    } else {
                        warehouse.setToolSlotStack(slot, handSnapshot.copyWithCount(1));
                    }
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, toolSnapshot.copy());
                    tx.commit();
                    if (player.containerMenu != null) {
                        player.containerMenu.broadcastChanges();
                    }
                    return true;
                });
            } catch (RuntimeException | Error throwable) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
                throw throwable;
            }
            if (!completed) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
            }
        });
    }

    private static PlayerWarehouse getWarehouse(ServerPlayer player) {
        return WarehouseService.get(player);
    }

    private static void syncChanges(ServerPlayer player) {
        syncChanges(player, "server_network.change");
    }

    private static void syncChanges(ServerPlayer player, String reason) {
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
        WarehouseService.commit(player, reason);
    }
}
