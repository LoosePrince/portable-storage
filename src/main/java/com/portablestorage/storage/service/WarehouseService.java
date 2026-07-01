package com.portablestorage.storage.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.storage.key.WarehouseStackKey;
import com.portablestorage.storage.sync.WarehouseSyncService;
import com.portablestorage.storage.world.PortablestorageSavedData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class WarehouseService {
    private WarehouseService() {
    }

    public static PortablestorageSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PortablestorageSavedData.TYPE);
    }

    public static PlayerWarehouse get(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        String ownerName = player == null ? null : player.getScoreboardName();
        return data(server).getOrCreateWarehouse(uuid, ownerName);
    }

    public static PlayerWarehouse get(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return null;
        }
        return data(server).getOrCreateWarehouse(player.getUUID(), player.getScoreboardName());
    }

    public static PlayerWarehouse get(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return get(serverPlayer);
        }
        if (player != null && player.level() instanceof ServerLevel level) {
            return data(level.getServer()).getOrCreateWarehouse(player.getUUID(), player.getScoreboardName());
        }
        return null;
    }

    public static Collection<PlayerWarehouse> getAll(MinecraftServer server) {
        return data(server).getAllWarehouses();
    }

    public static void markDirty(MinecraftServer server, UUID uuid, String reason) {
        if (server == null) {
            return;
        }
        data(server).markWarehouseDirty(uuid, reason);
    }

    public static void commit(MinecraftServer server, UUID uuid, String reason) {
        if (server == null || uuid == null) {
            return;
        }
        markDirty(server, uuid, reason);
        sync(server, uuid);
    }

    public static void commit(ServerPlayer player, String reason) {
        if (player == null) {
            return;
        }
        MinecraftServer server = serverOf(player);
        if (server == null) {
            return;
        }
        commit(server, player.getUUID(), reason);
        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }
    }

    public static boolean commitIfChanged(ServerPlayer player, String reason, BooleanSupplier change) {
        if (change == null || !change.getAsBoolean()) {
            return false;
        }
        commit(player, reason);
        return true;
    }

    public static boolean commitIfWarehouseChanged(ServerPlayer player, PlayerWarehouse warehouse, String reason,
            BooleanSupplier change) {
        if (player == null || warehouse == null || change == null) {
            return false;
        }
        Map<UUID, Long> before = sharedGroupRevisionSnapshot(warehouse);
        boolean changed = change.getAsBoolean() || hasSharedGroupChanged(warehouse, before);
        if (!changed) {
            return false;
        }
        commit(player, reason);
        return true;
    }

    public static <T> T commitIfWarehouseChanged(ServerPlayer player, PlayerWarehouse warehouse, String reason,
            Supplier<T> change) {
        if (change == null) {
            return null;
        }
        if (player == null || warehouse == null) {
            return change.get();
        }
        Map<UUID, Long> before = sharedGroupRevisionSnapshot(warehouse);
        T result = change.get();
        if (hasSharedGroupChanged(warehouse, before)) {
            commit(player, reason);
        }
        return result;
    }

    public static <T> T transaction(ServerPlayer player, PlayerWarehouse warehouse, String reason,
            Function<WarehouseTransaction, T> change) {
        if (change == null) {
            return null;
        }
        if (player == null || warehouse == null) {
            return change.apply(WarehouseTransaction.noop());
        }

        WarehouseTransaction transaction = WarehouseTransaction.capture(warehouse);
        try {
            T result = change.apply(transaction);
            if (transaction.isCommitted()) {
                commit(player, reason);
            } else {
                transaction.rollback();
            }
            return result;
        } catch (RuntimeException | Error throwable) {
            transaction.rollback();
            throw throwable;
        }
    }

    public static final class WarehouseTransaction {
        private final Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> storageSnapshot;
        private boolean committed;
        private boolean rolledBack;

        private WarehouseTransaction(Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> storageSnapshot) {
            this.storageSnapshot = storageSnapshot;
        }

        private static WarehouseTransaction capture(PlayerWarehouse warehouse) {
            Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> snapshot = new LinkedHashMap<>();
            if (warehouse != null) {
                for (PlayerWarehouse member : warehouse.getSharedGroupWarehouses()) {
                    snapshot.put(member, member.unifiedStorageSnapshot());
                }
            }
            return new WarehouseTransaction(snapshot);
        }

        private static WarehouseTransaction noop() {
            return new WarehouseTransaction(Map.of());
        }

        public void commit() {
            this.committed = true;
        }

        public boolean isCommitted() {
            return committed;
        }

        public void rollback() {
            if (rolledBack) {
                return;
            }
            rolledBack = true;
            for (Map.Entry<PlayerWarehouse, Map<WarehouseStackKey, Long>> entry : storageSnapshot.entrySet()) {
                entry.getKey().restoreUnifiedStorageSnapshot(entry.getValue());
            }
        }
    }

    public static Map<UUID, Long> sharedGroupRevisionSnapshot(PlayerWarehouse warehouse) {
        Map<UUID, Long> snapshot = new LinkedHashMap<>();
        if (warehouse == null) {
            return snapshot;
        }
        for (PlayerWarehouse member : warehouse.getSharedGroupWarehouses()) {
            snapshot.put(member.getOwnerUuid(), member.getStateRevision());
        }
        return snapshot;
    }

    public static boolean hasSharedGroupChanged(PlayerWarehouse warehouse, Map<UUID, Long> before) {
        if (warehouse == null || before == null) {
            return false;
        }
        Map<UUID, Long> after = sharedGroupRevisionSnapshot(warehouse);
        return !after.equals(before);
    }

    public static void sync(MinecraftServer server, UUID uuid) {
        WarehouseSyncService.sync(server, uuid);
    }

    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = serverOf(player);
        if (server != null) {
            sync(server, player.getUUID());
        }
    }

    public static void syncAll(MinecraftServer server) {
        WarehouseSyncService.syncAll(server);
    }

    private static MinecraftServer serverOf(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            return level.getServer();
        }
        return null;
    }
}