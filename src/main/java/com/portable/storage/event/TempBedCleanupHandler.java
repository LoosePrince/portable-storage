package com.portable.storage.event;

import com.portable.storage.net.ServerNetworkingHandlers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Set;

/**
 * 临时床清理处理器
 */
public class TempBedCleanupHandler {
    
    private static long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL = 20; // 每1秒检查一次
    
    /**
     * 定期清理临时床
     */
    public static void tick(MinecraftServer server) {
        long currentTime = server.getOverworld().getTime();
        
        // 检查是否需要清理
        if (currentTime - lastCleanupTime < CLEANUP_INTERVAL) {
            return;
        }
        
        lastCleanupTime = currentTime;
        
        // 遍历所有世界
        for (World world : server.getWorlds()) {
            cleanupTempBedsInWorld(world, currentTime);
        }
    }
    
    /**
     * 清理指定世界中的临时床
     */
    private static void cleanupTempBedsInWorld(World world, long currentTime) {
        Set<BlockPos> tempBedPositions = ServerNetworkingHandlers.getTempBedPositions();
        Iterator<BlockPos> iterator = tempBedPositions.iterator();
        
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            
            // 检查位置是否在当前世界中
            if (pos.getY() >= world.getBottomY() && pos.getY() <= world.getTopY()) {
                // 检查是否应该清理
                if (shouldCleanupTempBed(world, pos, currentTime)) {
                    // 清理完整的临时床
                    ServerNetworkingHandlers.cleanupCompleteTempBed(pos, world);
                    iterator.remove();
                }
            }
        }
    }
    
    /**
     * 检查是否应该清理临时床
     */
    private static boolean shouldCleanupTempBed(World world, BlockPos pos, long currentTime) {
        // 检查是否天亮
        if (world.isDay()) {
            return true;
        }
        
        // 检查是否超时（10秒未交互）
        return ServerNetworkingHandlers.isTempBedExpired(pos, currentTime);
    }
}
