package com.portablestorage.storage.sync;

import java.util.UUID;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.C2SRequestWarehouseSnapshotPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class ClientWarehouseState {
    private static PlayerWarehouse currentWarehouse;
    private static UUID currentOwnerUuid;
    private static WarehouseSnapshot pendingSnapshot;
    private static boolean snapshotApplied;
    private static boolean requestInFlight;

    private ClientWarehouseState() {
    }

    public static PlayerWarehouse current() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        return current(client.player, client.player.getUUID());
    }

    public static PlayerWarehouse current(Player player, UUID uuid) {
        UUID ownerUuid = uuid != null ? uuid : player == null ? null : player.getUUID();
        if (ownerUuid == null) {
            return null;
        }

        applyPendingIfReady(Minecraft.getInstance());
        ensureWarehouse(ownerUuid);
        if (!snapshotApplied && pendingSnapshot == null && !requestInFlight) {
            requestSnapshot();
        }
        return currentWarehouse;
    }

    public static void apply(WarehouseSnapshot snapshot) {
        if (snapshot == null || snapshot.ownerUuid() == null) {
            return;
        }
        pendingSnapshot = new WarehouseSnapshot(snapshot.ownerUuid(), snapshot.warehouseData().copy());
        requestInFlight = false;
        applyPendingIfReady(Minecraft.getInstance());
    }

    public static void onPlayReady(Minecraft client) {
        beginSession(client);
        requestSnapshot();
        tick(client);
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }
        applyPendingIfReady(client);
        current(client.player, client.player.getUUID());
    }

    public static void requestSnapshot() {
        if (!requestInFlight && ClientPlayNetworking.canSend(C2SRequestWarehouseSnapshotPayload.TYPE)) {
            requestInFlight = true;
            ClientPlayNetworking.send(new C2SRequestWarehouseSnapshotPayload());
        }
    }

    public static void clear() {
        currentWarehouse = null;
        currentOwnerUuid = null;
        pendingSnapshot = null;
        snapshotApplied = false;
        requestInFlight = false;
    }

    private static void beginSession(Minecraft client) {
        UUID ownerUuid = client != null && client.player != null ? client.player.getUUID() : null;
        if (ownerUuid == null) {
            clear();
            return;
        }

        boolean canKeepMenuBoundWarehouse = currentWarehouse != null && ownerUuid.equals(currentOwnerUuid);
        if (!canKeepMenuBoundWarehouse) {
            currentOwnerUuid = ownerUuid;
            currentWarehouse = new PlayerWarehouse(ownerUuid, __ -> {
            });
        } else if (snapshotApplied) {
            currentWarehouse.loadFromNbt(new CompoundTag(), RegistryAccess.EMPTY);
        }

        if (pendingSnapshot != null && !ownerUuid.equals(pendingSnapshot.ownerUuid())) {
            pendingSnapshot = null;
        }
        snapshotApplied = false;
        requestInFlight = false;
    }

    private static void ensureWarehouse(UUID ownerUuid) {
        if (currentWarehouse != null && ownerUuid.equals(currentOwnerUuid)) {
            return;
        }
        currentOwnerUuid = ownerUuid;
        currentWarehouse = new PlayerWarehouse(ownerUuid, __ -> {
        });
        snapshotApplied = false;
    }

    private static boolean applyPendingIfReady(Minecraft client) {
        if (pendingSnapshot == null || client == null || client.level == null) {
            return false;
        }

        WarehouseSnapshot snapshot = pendingSnapshot;
        ensureWarehouse(snapshot.ownerUuid());
        currentWarehouse.loadFromNbt(snapshot.warehouseData().copy(), client.level.registryAccess());
        currentOwnerUuid = snapshot.ownerUuid();
        pendingSnapshot = null;
        snapshotApplied = true;
        requestInFlight = false;
        return true;
    }
}