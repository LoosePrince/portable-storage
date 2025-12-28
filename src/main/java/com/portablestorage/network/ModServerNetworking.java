package com.portablestorage.network;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.screen.CraftingWarehouseScreenHandler;
import com.portablestorage.util.WarehouseSetting;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ModServerNetworking {

    public static void handleOpenCrafting(OpenCraftingPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new CraftingWarehouseScreenHandler(syncId, inventory, ContainerLevelAccess.create(player.level(), player.blockPosition())),
                Component.translatable("container.crafting")
            ));
        });
    }

    public static void handleUpdateSettings(UpdateSettingsPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            
                    switch (payload.setting()) {
                        case FOLD -> warehouse.setFolded(payload.value() == 1);
                        case SORT_MODE -> warehouse.setSortMode(payload.value());
                        case SORT_ORDER -> warehouse.setAscending(payload.value() == 1);
                        case QUICK_INTERACTION -> warehouse.setQuickInteraction(payload.value() == 1);
                        case SMART_COLLAPSE -> warehouse.setSmartCollapse(payload.value() == 1);
                        case CRAFT_REFILL -> warehouse.setCraftRefill(payload.value() == 1);
                    }
            syncChanges(player);
        });
    }

    public static void handleChangeRows(ChangeRowsPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            getWarehouse(player).setVisibleRows(getWarehouse(player).getVisibleRows() + payload.delta());
            syncChanges(player);
        });
    }

    public static void handleScroll(ScrollPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            getWarehouse(player).setScrollOffset(getWarehouse(player).getScrollOffset() - payload.delta());
            syncChanges(player);
        });
    }

    public static void handleSearch(SearchPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            getWarehouse(player).setSearchText(payload.searchText());
            syncChanges(player);
        });
    }

    public static void handleQuickTransfer(QuickTransferPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            
            int slotId = payload.slotId();
            if (slotId < 0 || slotId >= player.containerMenu.slots.size()) return;
            
            Slot slot = player.containerMenu.slots.get(slotId);
            if (!(slot.container instanceof PlayerWarehouse)) return;
            
            // 查找起始索引
            int warehouseStart = -1;
            for (int i = 0; i < player.containerMenu.slots.size(); i++) {
                if (player.containerMenu.slots.get(i).container == warehouse) {
                    warehouseStart = i;
                    break;
                }
            }
            
            if (warehouseStart != -1) {
                warehouse.tryTransferToInventory(slotId - warehouseStart, player);
                syncChanges(player);
            }
        });
    }

    public static void handleRefill(RefillPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled() || !warehouse.isCraftRefill()) return;

            net.minecraft.world.inventory.AbstractContainerMenu handler = player.containerMenu;
            
            ItemStack template = payload.targetStack();
            if (template.isEmpty() || payload.slotIds().isEmpty()) return;

            // 1. 获取所有需要补货的槽位（来自客户端的指定）
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

            if (targetIndices.isEmpty()) return;

            // 2. 计算总共需要的数量
            int maxStackSize = Math.min(template.getMaxStackSize(), player.getInventory().getMaxStackSize());
            int totalNeed = 0;
            int[] needs = new int[targetIndices.size()];
            
            for (int i = 0; i < targetIndices.size(); i++) {
                ItemStack stack = handler.getSlot(targetIndices.get(i)).getItem();
                int currentCount = stack.isEmpty() ? 0 : stack.getCount();
                needs[i] = Math.max(0, maxStackSize - currentCount);
                totalNeed += needs[i];
            }

            if (totalNeed <= 0) return;

            // 3. 从仓库取出物品（不强制匹配组件，兼容智能折叠）
            ItemStack taken = warehouse.takeMatching(template, totalNeed, false);
            if (taken.isEmpty()) return;

            // 4. 均分补给
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
                if (!anyAdded) break;
            }

            // 5. 应用分配结果
            for (int i = 0; i < targetIndices.size(); i++) {
                if (distribution[i] <= 0) continue;
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

    private static PlayerWarehouse getWarehouse(ServerPlayer player) {
        return ModComponents.get(player).getWarehouse(player.getUUID());
    }

    private static void syncChanges(ServerPlayer player) {
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }
}
