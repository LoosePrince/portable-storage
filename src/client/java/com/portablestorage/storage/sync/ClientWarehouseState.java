package com.portablestorage.storage.sync;

import java.util.UUID;

import com.portablestorage.client.gui.WarehouseStateSync;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.C2SRequestWarehouseSnapshotPayload;
import com.portablestorage.util.CompatibilityDebug;
import com.portablestorage.util.WarehouseSetting;

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
    /** 配置界面里修改过仓库偏好，等待本会话收到首个仓库快照后同步到仓库。 */
    private static boolean warehousePreferencesSyncQueued;

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
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        // CRITICAL FIX: Only ever manage the local player's warehouse on the client.
        // Ignore queries for non-local entities (like Fake Players).
        UUID localUuid = client.player.getUUID();

        applyPendingIfReady(client);
        ensureWarehouse(localUuid);
        if (!snapshotApplied && pendingSnapshot == null && !requestInFlight) {
            requestSnapshot();
        }
        return currentWarehouse;
    }

    public static void apply(WarehouseSnapshot snapshot) {
        Minecraft client = Minecraft.getInstance();
        if (snapshot == null || snapshot.ownerUuid() == null || client.player == null) {
            return;
        }
        // ONLY accept snapshots matching the local human player
        if (!snapshot.ownerUuid().equals(client.player.getUUID())) {
            CompatibilityDebug.log("sync", () -> "rejected warehouse snapshot for non-local owner=" + snapshot.ownerUuid());
            return;
        }
        CompatibilityDebug.log("sync", () -> "accepted warehouse snapshot owner=" + snapshot.ownerUuid()
                + "; nbtKeys=" + snapshot.warehouseData().keySet());
        pendingSnapshot = new WarehouseSnapshot(snapshot.ownerUuid(), snapshot.warehouseData().copy());
        requestInFlight = false;
        applyPendingIfReady(client);
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
            CompatibilityDebug.log("sync", () -> "requesting local warehouse snapshot");
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

    /**
     * 配置界面（含主菜单 ModMenu 进入）修改仓库偏好后调用。
     * 进入世界并成功应用首个仓库快照时，这些本地偏好会被同步到当前玩家的仓库。
     */
    public static void markWarehousePreferencesDirty() {
        warehousePreferencesSyncQueued = true;
    }

    private static void syncStoredWarehousePreferences(PlayerWarehouse warehouse) {
        if (warehouse == null) {
            return;
        }
        // 把本地偏好（client.warehouse*）应用到客户端镜像并推送到服务端仓库。
        WarehouseStateSync.applySetting(warehouse, WarehouseSetting.SORT_MODE, ModConfig.warehouseSortMode);
        WarehouseStateSync.sendSetting(WarehouseSetting.SORT_MODE, ModConfig.warehouseSortMode);
        int ascending = ModConfig.warehouseAscending ? 1 : 0;
        WarehouseStateSync.applySetting(warehouse, WarehouseSetting.SORT_ORDER, ascending);
        WarehouseStateSync.sendSetting(WarehouseSetting.SORT_ORDER, ascending);
        int quick = ModConfig.warehouseQuickInteraction ? 1 : 0;
        WarehouseStateSync.applySetting(warehouse, WarehouseSetting.QUICK_INTERACTION, quick);
        WarehouseStateSync.sendSetting(WarehouseSetting.QUICK_INTERACTION, quick);
        int smartCollapse = ModConfig.warehouseSmartCollapse ? 1 : 0;
        WarehouseStateSync.applySetting(warehouse, WarehouseSetting.SMART_COLLAPSE, smartCollapse);
        WarehouseStateSync.sendSetting(WarehouseSetting.SMART_COLLAPSE, smartCollapse);
        int craftRefill = ModConfig.warehouseCraftRefill ? 1 : 0;
        WarehouseStateSync.applySetting(warehouse, WarehouseSetting.CRAFT_REFILL, craftRefill);
        WarehouseStateSync.sendSetting(WarehouseSetting.CRAFT_REFILL, craftRefill);
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
        CompatibilityDebug.log("sync", () -> "applied warehouse snapshot owner=" + snapshot.ownerUuid());
        pendingSnapshot = null;
        snapshotApplied = true;
        requestInFlight = false;

        // 会话首个快照落地后，把配置界面里保存的仓库偏好同步到仓库（一次性，随后清除标记）。
        if (warehousePreferencesSyncQueued) {
            warehousePreferencesSyncQueued = false;
            syncStoredWarehousePreferences(currentWarehouse);
        }
        return true;
    }
}