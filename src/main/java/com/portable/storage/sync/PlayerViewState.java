package com.portable.storage.sync;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * 玩家查看状态管理器
 * 跟踪哪些玩家正在查看仓库界面
 */
public class PlayerViewState {
    private static final Set<UUID> VIEWING_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PENDING_SYNC_PLAYERS = ConcurrentHashMap.newKeySet();
    
    /**
     * 标记玩家开始查看仓库界面
     */
    public static void startViewing(UUID playerId) {
        VIEWING_PLAYERS.add(playerId);
        // 清除待同步标记，因为现在会立即同步
        PENDING_SYNC_PLAYERS.remove(playerId);
    }
    
    /**
     * 标记玩家停止查看仓库界面
     */
    public static void stopViewing(UUID playerId) {
        VIEWING_PLAYERS.remove(playerId);
    }
    
    /**
     * 检查玩家是否正在查看仓库界面
     */
    public static boolean isViewing(UUID playerId) {
        return VIEWING_PLAYERS.contains(playerId);
    }
    
    /**
     * 标记玩家有待同步的变化
     */
    public static void markPendingSync(UUID playerId) {
        if (!isViewing(playerId)) {
            PENDING_SYNC_PLAYERS.add(playerId);
        }
    }
    
    /**
     * 获取所有待同步的玩家
     */
    public static Set<UUID> getPendingSyncPlayers() {
        return Set.copyOf(PENDING_SYNC_PLAYERS);
    }
    
    /**
     * 清除玩家的待同步标记
     */
    public static void clearPendingSync(UUID playerId) {
        PENDING_SYNC_PLAYERS.remove(playerId);
    }
    
    /**
     * 获取所有正在查看的玩家
     */
    public static Set<UUID> getViewingPlayers() {
        return Set.copyOf(VIEWING_PLAYERS);
    }
    
    /**
     * 清理玩家状态（玩家离线时调用）
     */
    public static void cleanupPlayer(UUID playerId) {
        VIEWING_PLAYERS.remove(playerId);
        PENDING_SYNC_PLAYERS.remove(playerId);
    }
    
    /**
     * 获取状态统计信息
     */
    public static String getStats() {
        return String.format("Viewing: %d, Pending: %d", 
            VIEWING_PLAYERS.size(), PENDING_SYNC_PLAYERS.size());
    }
}
