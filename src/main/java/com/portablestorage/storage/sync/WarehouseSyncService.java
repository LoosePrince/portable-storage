package com.portablestorage.storage.sync;

import java.util.UUID;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.S2CWarehouseSnapshotPayload;
import com.portablestorage.storage.service.WarehouseService;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class WarehouseSyncService {
    private WarehouseSyncService() {
    }

    public static void sync(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }
        PlayerWarehouse warehouse = WarehouseService.get(server, uuid);
        if (warehouse == null) {
            return;
        }
        ServerPlayNetworking.send(player, new S2CWarehouseSnapshotPayload(
                WarehouseSnapshot.fromWarehouse(warehouse, server.registryAccess())));
    }

    public static void sync(ServerPlayer player) {
        if (player != null && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            sync(level.getServer(), player.getUUID());
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }
}