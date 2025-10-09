package com.portable.storage.sync;

import com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload;
import com.portable.storage.storage.StorageInventory;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 变化积攒器
 * 积攒玩家的仓库变化，支持批量处理和延迟同步
 */
public class ChangeAccumulator {
    private static final ScheduledExecutorService ACCUMULATOR_EXECUTOR = Executors.newScheduledThreadPool(1);
    private static final Map<UUID, PlayerChangeBuffer> PLAYER_CHANGES = new ConcurrentHashMap<>();
    private static final int MAX_BATCH_SIZE = 50; // 最大批量大小
    
    /**
     * 玩家变化缓冲区
     */
    private static class PlayerChangeBuffer {
        private final Map<Integer, IncrementalStorageSyncS2CPayload.StorageChange> changes = new HashMap<>();
        private boolean needsFullSync = false;
        
        public PlayerChangeBuffer(UUID playerId) {
            // 构造函数，playerId参数保留用于未来扩展
        }
        
        public void addChange(IncrementalStorageSyncS2CPayload.StorageChange change) {
            int slotIndex = change.slotIndex();
            
            // 如果新变化是REMOVE，直接移除该槽位的所有变化
            if (change.type() == IncrementalStorageSyncS2CPayload.ChangeType.REMOVE) {
                changes.remove(slotIndex);
                changes.put(slotIndex, change);
            } else {
                // 对于ADD/UPDATE，合并变化
                IncrementalStorageSyncS2CPayload.StorageChange existing = changes.get(slotIndex);
                if (existing != null) {
                    // 如果之前是REMOVE，现在又ADD/UPDATE，保留新的变化
                    if (existing.type() == IncrementalStorageSyncS2CPayload.ChangeType.REMOVE) {
                        changes.put(slotIndex, change);
                    } else {
                        // 合并UPDATE变化
                        changes.put(slotIndex, change);
                    }
                } else {
                    changes.put(slotIndex, change);
                }
            }
            
        }
        
        public void markFullSync() {
            needsFullSync = true;
            changes.clear();
        }
        
        public List<IncrementalStorageSyncS2CPayload.StorageChange> getChanges() {
            return new ArrayList<>(changes.values());
        }
        
        public boolean isEmpty() {
            return changes.isEmpty() && !needsFullSync;
        }
        
        public boolean isFullSync() {
            return needsFullSync;
        }
        
        public void clear() {
            changes.clear();
            needsFullSync = false;
        }
    }
    
    /**
     * 添加变化到积攒器
     */
    public static void addChange(UUID playerId, IncrementalStorageSyncS2CPayload.StorageChange change) {
        PlayerChangeBuffer buffer = PLAYER_CHANGES.computeIfAbsent(playerId, PlayerChangeBuffer::new);
        buffer.addChange(change);
        
        // 如果玩家正在查看，立即同步
        if (PlayerViewState.isViewing(playerId)) {
            scheduleImmediateSync(playerId);
        } else {
            // 否则标记为待同步
            PlayerViewState.markPendingSync(playerId);
        }
    }
    
    /**
     * 标记需要全量同步
     */
    public static void markFullSync(UUID playerId) {
        PlayerChangeBuffer buffer = PLAYER_CHANGES.computeIfAbsent(playerId, PlayerChangeBuffer::new);
        buffer.markFullSync();
        
        if (PlayerViewState.isViewing(playerId)) {
            scheduleImmediateSync(playerId);
        } else {
            PlayerViewState.markPendingSync(playerId);
        }
    }
    
    /**
     * 立即同步（用于正在查看的玩家）
     */
    private static void scheduleImmediateSync(UUID playerId) {
        ACCUMULATOR_EXECUTOR.schedule(() -> {
            // 这里需要传入玩家实体，暂时跳过
            // 实际使用时会在有玩家实体时直接调用processPlayerChanges
        }, 10, TimeUnit.MILLISECONDS); // 很短的延迟，避免过于频繁的同步
    }
    
    /**
     * 处理玩家的积攒变化
     */
    public static void processPlayerChanges(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerChangeBuffer buffer = PLAYER_CHANGES.get(playerId);
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        
        try {
            if (buffer.isFullSync()) {
                // 全量同步
                StorageInventory currentInventory = com.portable.storage.net.ServerNetworkingHandlers.buildMergedSnapshot(player);
                StorageSyncManager.sendIncrementalSync(player, currentInventory);
            } else {
                // 增量同步
                List<IncrementalStorageSyncS2CPayload.StorageChange> changes = buffer.getChanges();
                if (!changes.isEmpty()) {
                    // 限制批量大小
                    if (changes.size() > MAX_BATCH_SIZE) {
                        // 如果变化太多，改为全量同步
                        StorageInventory currentInventory = com.portable.storage.net.ServerNetworkingHandlers.buildMergedSnapshot(player);
                        StorageSyncManager.sendIncrementalSync(player, currentInventory);
                    } else {
                        // 发送积攒的变化
                        sendAccumulatedChanges(player, changes);
                    }
                }
            }
            
            // 清除已处理的变化
            buffer.clear();
            
        } catch (Exception e) {
            com.portable.storage.PortableStorage.LOGGER.error("Failed to process accumulated changes for player {}", playerId, e);
            // 出错时标记需要全量同步
            buffer.markFullSync();
        }
    }
    
    /**
     * 发送积攒的变化
     */
    private static void sendAccumulatedChanges(ServerPlayerEntity player, List<IncrementalStorageSyncS2CPayload.StorageChange> changes) {
        // 这里需要实现一个简化的同步机制，直接发送变化
        // 由于StorageSyncManager的复杂性，我们暂时使用全量同步
        StorageInventory currentInventory = com.portable.storage.net.ServerNetworkingHandlers.buildMergedSnapshot(player);
        StorageSyncManager.sendIncrementalSync(player, currentInventory);
    }
    
    /**
     * 处理所有待同步的玩家
     */
    public static void processPendingSyncs(Collection<ServerPlayerEntity> onlinePlayers) {
        Set<UUID> pendingPlayers = PlayerViewState.getPendingSyncPlayers();
        for (UUID playerId : pendingPlayers) {
            // 查找对应的在线玩家
            ServerPlayerEntity player = onlinePlayers.stream()
                .filter(p -> p.getUuid().equals(playerId))
                .findFirst()
                .orElse(null);
            
            if (player != null) {
                processPlayerChanges(player);
            } else {
                // 玩家离线，清理缓冲区
                PLAYER_CHANGES.remove(playerId);
            }
        }
        // 清除所有待同步标记
        for (UUID playerId : pendingPlayers) {
            PlayerViewState.clearPendingSync(playerId);
        }
    }
    
    /**
     * 清理玩家数据
     */
    public static void cleanupPlayer(UUID playerId) {
        PLAYER_CHANGES.remove(playerId);
        PlayerViewState.cleanupPlayer(playerId);
    }
    
    /**
     * 获取积攒器统计信息
     */
    public static String getStats() {
        return String.format("Buffered players: %d, %s", 
            PLAYER_CHANGES.size(), PlayerViewState.getStats());
    }
    
    /**
     * 关闭积攒器
     */
    public static void shutdown() {
        ACCUMULATOR_EXECUTOR.shutdown();
    }
}
