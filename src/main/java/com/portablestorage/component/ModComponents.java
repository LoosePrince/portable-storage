package com.portablestorage.component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.portablestorage.storage.service.WarehouseService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class ModComponents {
    private static volatile ClientWarehouseProvider clientWarehouseProvider = (player, uuid) -> null;

    private ModComponents() {
    }

    public static WarehouseComponent get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverFacade(serverLevel.getServer());
        }
        return clientFacade(null);
    }

    public static WarehouseComponent get(Entity entity) {
        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
            return serverFacade(serverLevel.getServer());
        }
        Level level = entity.level();
        if (level instanceof ServerLevel serverLevel) {
            return serverFacade(serverLevel.getServer());
        }
        return clientFacade(entity instanceof Player player ? player : null);
    }

    public static PlayerWarehouse getWarehouse(MinecraftServer server, UUID uuid) {
        return WarehouseService.get(server, uuid);
    }

    public static PlayerWarehouse getWarehouse(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return WarehouseService.get(serverPlayer);
        }
        if (player != null && player.level() instanceof ServerLevel level) {
            return WarehouseService.get(level.getServer(), player.getUUID());
        }
        return getClientWarehouse(player, player == null ? null : player.getUUID());
    }

    public static PlayerWarehouse getWarehouse(ServerPlayer player) {
        return WarehouseService.get(player);
    }

    public static void sync(ServerPlayer player) {
        WarehouseService.sync(player);
    }

    public static void sync(MinecraftServer server, UUID uuid) {
        WarehouseService.sync(server, uuid);
    }

    public static void setClientWarehouseProvider(ClientWarehouseProvider provider) {
        clientWarehouseProvider = provider == null ? (player, uuid) -> null : provider;
    }

    private static PlayerWarehouse getClientWarehouse(Player player, UUID uuid) {
        return clientWarehouseProvider.get(player, uuid);
    }

    private static WarehouseComponent serverFacade(MinecraftServer server) {
        return new ServerWarehouseComponent(server);
    }

    private static WarehouseComponent clientFacade(Player player) {
        return new ClientWarehouseComponent(player);
    }

    @FunctionalInterface
    public interface ClientWarehouseProvider {
        PlayerWarehouse get(Player player, UUID uuid);
    }

    private record ServerWarehouseComponent(MinecraftServer server) implements WarehouseComponent {
        @Override
        public PlayerWarehouse getWarehouse(UUID uuid) {
            return WarehouseService.get(server, uuid);
        }

        @Override
        public Collection<PlayerWarehouse> getAllWarehouses() {
            if (server == null) {
                return List.of();
            }
            return WarehouseService.getAll(server);
        }

        @Override
        public void syncForPlayer(UUID uuid) {
            WarehouseService.sync(server, uuid);
        }
    }

    private record ClientWarehouseComponent(Player player) implements WarehouseComponent {
        @Override
        public PlayerWarehouse getWarehouse(UUID uuid) {
            return getClientWarehouse(player, uuid);
        }

        @Override
        public Collection<PlayerWarehouse> getAllWarehouses() {
            PlayerWarehouse warehouse = getWarehouse(player == null ? null : player.getUUID());
            return warehouse == null ? List.of() : List.of(warehouse);
        }

        @Override
        public void syncForPlayer(UUID uuid) {
        }
    }
}
