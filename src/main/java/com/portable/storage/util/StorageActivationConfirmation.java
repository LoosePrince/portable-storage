package com.portable.storage.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 仓库激活确认管理器
 * 管理玩家使用仓库激活物品时的二次确认机制
 */
public class StorageActivationConfirmation {
    
    private static final Map<UUID, Long> pendingConfirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 5000; // 5秒超时
    
    /**
     * 检查玩家是否有待确认的激活请求
     */
    public static boolean hasPendingConfirmation(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        Long timestamp = pendingConfirmations.get(playerId);
        
        if (timestamp == null) {
            return false;
        }
        
        // 检查是否超时
        if (System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT) {
            pendingConfirmations.remove(playerId);
            return false;
        }
        
        return true;
    }
    
    /**
     * 设置待确认的激活请求
     */
    public static void setPendingConfirmation(ServerPlayerEntity player) {
        pendingConfirmations.put(player.getUuid(), System.currentTimeMillis());
    }
    
    /**
     * 确认激活请求
     */
    public static boolean confirmActivation(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        Long timestamp = pendingConfirmations.get(playerId);
        
        if (timestamp == null) {
            return false;
        }
        
        // 检查是否超时
        if (System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT) {
            pendingConfirmations.remove(playerId);
            return false;
        }
        
        // 确认成功，移除待确认状态
        pendingConfirmations.remove(playerId);
        return true;
    }
    
    /**
     * 取消待确认的激活请求
     */
    public static void cancelPendingConfirmation(ServerPlayerEntity player) {
        pendingConfirmations.remove(player.getUuid());
    }
    
    /**
     * 清理超时的确认请求
     */
    public static void cleanupExpiredConfirmations() {
        long currentTime = System.currentTimeMillis();
        pendingConfirmations.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > CONFIRMATION_TIMEOUT);
    }
}
