package com.portablestorage.network;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ModNetworking {
    public static void registerC2SPayloads() {
        PayloadTypeRegistry.playC2S().register(ScrollPayload.TYPE, ScrollPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SearchPayload.TYPE, SearchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChangeRowsPayload.TYPE, ChangeRowsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuickTransferPayload.ID, QuickTransferPayload.CODEC);
    }

    public static void registerS2CPayloads() {
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
    }

    public static void registerServerReceivers() {
        // 统一处理逻辑：获取仓库 -> 执行操作 -> 广播变更
        ServerPlayNetworking.registerGlobalReceiver(UpdateSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                PlayerWarehouse warehouse = getWarehouse(player);
                
                switch (payload.settingType()) {
                    case 0 -> warehouse.setFolded(payload.value() == 1);
                    case 1 -> warehouse.setSortMode(payload.value());
                    case 2 -> warehouse.setAscending(payload.value() == 1);
                    case 3 -> warehouse.setQuickInteraction(payload.value() == 1);
                }
                syncChanges(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ChangeRowsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                PlayerWarehouse warehouse = getWarehouse(player);
                warehouse.setVisibleRows(warehouse.getVisibleRows() + payload.delta());
                syncChanges(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ScrollPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                PlayerWarehouse warehouse = getWarehouse(player);
                warehouse.setScrollOffset(warehouse.getScrollOffset() - payload.delta());
                syncChanges(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SearchPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                getWarehouse(player).setSearchText(payload.searchText());
                syncChanges(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(QuickTransferPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                PlayerWarehouse warehouse = getWarehouse(player);
                
                // 快捷交互必须开启
                if (!warehouse.isQuickInteraction() || !warehouse.isEnabled()) return;
                
                // 获取点击的槽位
                int slotId = payload.slotId();
                if (slotId < 0 || slotId >= player.containerMenu.slots.size()) return;
                
                Slot slot = player.containerMenu.slots.get(slotId);
                if (!(slot.container instanceof PlayerWarehouse)) return;
                
                // 查找仓库起始槽位索引
                int warehouseStart = -1;
                for (int i = 0; i < player.containerMenu.slots.size(); i++) {
                    if (player.containerMenu.slots.get(i).container == warehouse) {
                        warehouseStart = i;
                        break;
                    }
                }
                if (warehouseStart == -1) return;
                
                int relativeIndex = slotId - warehouseStart;
                if (relativeIndex < 0) return;
                
                // 获取实际数量并尝试转移
                ItemStack stackInSlot = slot.getItem();
                if (stackInSlot.isEmpty()) return;
                
                long realCount = warehouse.getRealCount(relativeIndex);
                int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
                ItemStack resultStack = stackInSlot.copyWithCount(toTake);
                
                // 尝试手动转移到背包：遍历背包槽位找空位或可堆叠位置
                boolean transferred = false;
                for (int i = WarehouseConstants.PLAYER_INVENTORY_START; i < WarehouseConstants.PLAYER_INVENTORY_END && !resultStack.isEmpty(); i++) {
                    Slot targetSlot = player.containerMenu.slots.get(i);
                    ItemStack targetStack = targetSlot.getItem();
                    
                    if (targetStack.isEmpty()) {
                        // 空槽位，直接放入
                        targetSlot.set(resultStack.copy());
                        transferred = true;
                        resultStack = ItemStack.EMPTY;
                    } else if (ItemStack.isSameItemSameComponents(targetStack, resultStack)) {
                        // 可堆叠
                        int maxStackSize = targetStack.getMaxStackSize();
                        int canAdd = maxStackSize - targetStack.getCount();
                        if (canAdd > 0) {
                            int adding = Math.min(canAdd, resultStack.getCount());
                            targetStack.grow(adding);
                            resultStack.shrink(adding);
                            transferred = true;
                            if (resultStack.isEmpty()) break;
                        }
                    }
                }
                
                // 计算实际移动的数量
                if (transferred) {
                    int movedCount = toTake - resultStack.getCount();
                    if (movedCount > 0) {
                        warehouse.removeItem(relativeIndex, movedCount);
                    }
                }
                
                syncChanges(player);
            });
        });
    }

    private static PlayerWarehouse getWarehouse(ServerPlayer player) {
        return ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
    }

    private static void syncChanges(ServerPlayer player) {
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }
}

