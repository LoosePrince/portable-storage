package com.portable.storage.sync;

import com.portable.storage.PortableStorage;
import com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload;
import com.portable.storage.storage.StorageInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.RegistryOps;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;

/**
 * 仓库同步管理器
 * 负责管理增量同步和异步处理
 */
public class StorageSyncManager {
    private static final ExecutorService SYNC_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Map<UUID, PlayerSyncState> PLAYER_SYNC_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SYNC_IDS = new ConcurrentHashMap<>();
    
    /**
     * 玩家同步状态
     */
    private static class PlayerSyncState {
        private final Map<Integer, ItemStack> lastKnownSlots = new HashMap<>();
        private final Map<Integer, Long> lastKnownCounts = new HashMap<>();
        private long lastSyncId = 0;
        private boolean needsFullSync = true;
        
        public void updateSlot(int index, ItemStack stack, long count) {
            lastKnownSlots.put(index, stack.copy());
            lastKnownCounts.put(index, count);
        }
        
        public long getNextSyncId() {
            return ++lastSyncId;
        }
    }
    
    /**
     * 按需发送增量同步（只对正在查看的玩家发送）
     */
    public static CompletableFuture<Void> sendIncrementalSyncOnDemand(ServerPlayerEntity player, StorageInventory currentInventory) {
        UUID playerId = player.getUuid();
        
        // 检查是否启用按需同步
        if (com.portable.storage.config.ServerConfig.getInstance().isEnableIncrementalSync()) {
            // 如果玩家正在查看，立即同步
            if (PlayerViewState.isViewing(playerId)) {
                return sendIncrementalSync(player, currentInventory);
            } else {
                // 否则积攒变化
                ChangeAccumulator.markFullSync(playerId);
                return CompletableFuture.completedFuture(null);
            }
        } else {
            // 回退到传统全量同步
            return CompletableFuture.runAsync(() -> {
                com.portable.storage.net.ServerNetworkingHandlers.sendSync(player);
            }, SYNC_EXECUTOR);
        }
    }
    
    /**
     * 异步发送增量同步
     */
    public static CompletableFuture<Void> sendIncrementalSync(ServerPlayerEntity player, StorageInventory currentInventory) {
        return CompletableFuture.runAsync(() -> {
            try {
                UUID playerId = player.getUuid();
                PlayerSyncState state = PLAYER_SYNC_STATES.computeIfAbsent(playerId, k -> new PlayerSyncState());
                
                List<IncrementalStorageSyncS2CPayload.StorageChange> changes = new ArrayList<>();
                
                boolean isFullSync = state.needsFullSync;
                if (state.needsFullSync) {
                    // 全量同步
                    changes = buildFullSyncChanges(currentInventory, player.getRegistryManager());
                    state.needsFullSync = false;
                } else {
                    // 增量同步
                    changes = buildIncrementalChanges(state, currentInventory, player.getRegistryManager());
                }
                
                if (!changes.isEmpty()) {
                    long syncId = state.getNextSyncId();
                    LAST_SYNC_IDS.put(playerId, syncId);
                    
                    IncrementalStorageSyncS2CPayload payload = new IncrementalStorageSyncS2CPayload(
                        syncId, changes, isFullSync
                    );
                    
                    PortableStorage.LOGGER.debug("Sending {} sync to player {} with {} changes", 
                        isFullSync ? "full" : "incremental", player.getName().getString(), changes.size());
                    
                    // 在主线程发送网络包
                    player.server.execute(() -> {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
                    });
                    
                    // 更新状态
                    updateSyncState(state, currentInventory);
                } else {
                    PortableStorage.LOGGER.debug("No changes to sync for player {}", player.getName().getString());
                }
            } catch (Exception e) {
                PortableStorage.LOGGER.error("Failed to send incremental sync to player {}", player.getName().getString(), e);
                // 标记需要全量同步
                PLAYER_SYNC_STATES.get(player.getUuid()).needsFullSync = true;
            }
        }, SYNC_EXECUTOR);
    }
    
    /**
     * 构建全量同步变化
     */
    private static List<IncrementalStorageSyncS2CPayload.StorageChange> buildFullSyncChanges(
            StorageInventory inventory, RegistryWrapper.WrapperLookup lookup) {
        List<IncrementalStorageSyncS2CPayload.StorageChange> changes = new ArrayList<>();
        
        for (int i = 0; i < inventory.getCapacity(); i++) {
            ItemStack stack = inventory.getDisplayStack(i);
            if (!stack.isEmpty()) {
                long count = inventory.getCountByIndex(i);
                if (count > 0) {
                    NbtCompound data = new NbtCompound();
                    // 使用CODEC序列化ItemStack
                    final DynamicOps<net.minecraft.nbt.NbtElement> ops =
                        (lookup != null) ? RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
                    var encoded = ItemStack.CODEC.encodeStart(ops, stack);
                    encoded.result().ifPresent(nbtElement -> data.put("item", nbtElement));
                    data.putLong("count", count);
                    data.putLong("timestamp", inventory.getTimestampByIndex(i));
                    
                    changes.add(new IncrementalStorageSyncS2CPayload.StorageChange(
                        i, IncrementalStorageSyncS2CPayload.ChangeType.ADD.ordinal(), data
                    ));
                }
            }
        }
        
        return changes;
    }
    
    /**
     * 构建增量同步变化
     */
    private static List<IncrementalStorageSyncS2CPayload.StorageChange> buildIncrementalChanges(
            PlayerSyncState state, StorageInventory inventory, RegistryWrapper.WrapperLookup lookup) {
        List<IncrementalStorageSyncS2CPayload.StorageChange> changes = new ArrayList<>();
        
        // 检查现有槽位的变化
        for (int i = 0; i < inventory.getCapacity(); i++) {
            ItemStack currentStack = inventory.getDisplayStack(i);
            long currentCount = inventory.getCountByIndex(i);
            
            ItemStack lastStack = state.lastKnownSlots.get(i);
            Long lastCount = state.lastKnownCounts.get(i);
            
            if (currentStack.isEmpty()) {
                // 当前为空，但之前有物品
                if (lastStack != null && !lastStack.isEmpty()) {
                    changes.add(new IncrementalStorageSyncS2CPayload.StorageChange(
                        i, IncrementalStorageSyncS2CPayload.ChangeType.REMOVE.ordinal(), new NbtCompound()
                    ));
                }
            } else {
                // 当前有物品
                if (lastStack == null || lastStack.isEmpty()) {
                    // 新增物品
                    NbtCompound data = new NbtCompound();
                    // 使用CODEC序列化ItemStack
                    final DynamicOps<net.minecraft.nbt.NbtElement> ops =
                        (lookup != null) ? RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
                    var encoded = ItemStack.CODEC.encodeStart(ops, currentStack);
                    encoded.result().ifPresent(nbtElement -> data.put("item", nbtElement));
                    data.putLong("count", currentCount);
                    data.putLong("timestamp", inventory.getTimestampByIndex(i));
                    
                    changes.add(new IncrementalStorageSyncS2CPayload.StorageChange(
                        i, IncrementalStorageSyncS2CPayload.ChangeType.ADD.ordinal(), data
                    ));
                } else if (!ItemStack.areItemsAndComponentsEqual(currentStack, lastStack) || 
                          !Objects.equals(currentCount, lastCount)) {
                    // 物品或数量发生变化
                    NbtCompound data = new NbtCompound();
                    // 使用CODEC序列化ItemStack
                    final DynamicOps<net.minecraft.nbt.NbtElement> ops =
                        (lookup != null) ? RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
                    var encoded = ItemStack.CODEC.encodeStart(ops, currentStack);
                    encoded.result().ifPresent(nbtElement -> data.put("item", nbtElement));
                    data.putLong("count", currentCount);
                    data.putLong("timestamp", inventory.getTimestampByIndex(i));
                    
                    changes.add(new IncrementalStorageSyncS2CPayload.StorageChange(
                        i, IncrementalStorageSyncS2CPayload.ChangeType.UPDATE.ordinal(), data
                    ));
                }
            }
        }
        
        // 检查是否有槽位被移除（当前容量小于之前记录的最大槽位）
        int currentCapacity = inventory.getCapacity();
        for (Map.Entry<Integer, ItemStack> entry : state.lastKnownSlots.entrySet()) {
            int slotIndex = entry.getKey();
            if (slotIndex >= currentCapacity && !entry.getValue().isEmpty()) {
                changes.add(new IncrementalStorageSyncS2CPayload.StorageChange(
                    slotIndex, IncrementalStorageSyncS2CPayload.ChangeType.REMOVE.ordinal(), new NbtCompound()
                ));
            }
        }
        
        return changes;
    }
    
    /**
     * 更新同步状态
     */
    private static void updateSyncState(PlayerSyncState state, StorageInventory inventory) {
        // 更新新状态（不清空，保持增量同步的基础）
        for (int i = 0; i < inventory.getCapacity(); i++) {
            ItemStack stack = inventory.getDisplayStack(i);
            if (!stack.isEmpty()) {
                long count = inventory.getCountByIndex(i);
                if (count > 0) {
                    state.updateSlot(i, stack, count);
                } else {
                    // 如果数量为0，移除该槽位
                    state.lastKnownSlots.remove(i);
                    state.lastKnownCounts.remove(i);
                }
            } else {
                // 如果槽位为空，移除该槽位
                state.lastKnownSlots.remove(i);
                state.lastKnownCounts.remove(i);
            }
        }
    }
    
    /**
     * 处理同步确认
     */
    public static void handleSyncAck(UUID playerId, long syncId, boolean success) {
        Long lastSyncId = LAST_SYNC_IDS.get(playerId);
        if (lastSyncId != null && lastSyncId.equals(syncId)) {
            if (!success) {
                // 同步失败，标记需要全量同步
                PlayerSyncState state = PLAYER_SYNC_STATES.get(playerId);
                if (state != null) {
                    state.needsFullSync = true;
                }
            }
            LAST_SYNC_IDS.remove(playerId);
        }
    }
    
    /**
     * 清理玩家同步状态
     */
    public static void cleanupPlayer(UUID playerId) {
        PLAYER_SYNC_STATES.remove(playerId);
        LAST_SYNC_IDS.remove(playerId);
    }
    
    /**
     * 强制全量同步
     */
    public static void forceFullSync(UUID playerId) {
        PlayerSyncState state = PLAYER_SYNC_STATES.get(playerId);
        if (state != null) {
            state.needsFullSync = true;
        }
    }
    
    /**
     * 关闭同步管理器
     */
    public static void shutdown() {
        SYNC_EXECUTOR.shutdown();
    }
}
