package com.portable.storage.sync;

import java.util.Collection;
import java.util.UUID;

/**
 * 增量同步已移除：积攒器降级为空实现以保持兼容。
 */
public class ChangeAccumulator {
    public static void addChange(UUID playerId, Object ignored) {}
    public static void markFullSync(UUID playerId) {}
    public static void processPlayerChanges(net.minecraft.server.network.ServerPlayerEntity player) {}
    public static void processPendingSyncs(Collection<net.minecraft.server.network.ServerPlayerEntity> onlinePlayers) {}
    public static void cleanupPlayer(UUID playerId) {}
    public static String getStats() { return "disabled"; }
}
