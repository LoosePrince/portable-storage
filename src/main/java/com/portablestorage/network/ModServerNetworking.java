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

    private static PlayerWarehouse getWarehouse(ServerPlayer player) {
        return ModComponents.get(player).getWarehouse(player.getUUID());
    }

    private static void syncChanges(ServerPlayer player) {
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }
}
