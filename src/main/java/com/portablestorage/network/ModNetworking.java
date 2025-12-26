package com.portablestorage.network;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class ModNetworking {
    public static void registerC2SPayloads() {
        PayloadTypeRegistry.playC2S().register(ScrollPayload.TYPE, ScrollPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SearchPayload.TYPE, SearchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChangeRowsPayload.TYPE, ChangeRowsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.CODEC);
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

