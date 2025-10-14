package com.portable.storage.event;

import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.sync.PlayerViewState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 周期性增量同步调度：
 * 每 N tick 遍历正在查看界面的玩家并发送增量 diff。
 * 后续可接入服务器配置与 ChangeAccumulator 批次聚合。
 */
public final class IncrementalSyncTickHandler {
    private IncrementalSyncTickHandler() {}

    private static int tickCounter = 0;
    private static int intervalTicks = 2; // 从配置读取

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            // 读取配置（允许运行时调整）
            try {
                var cfg = com.portable.storage.config.ServerConfig.getInstance();
                if (!cfg.isEnableIncrementalSync()) return; // 关闭增量则不推送
                intervalTicks = Math.max(1, cfg.getIncrementalSyncIntervalTicks());
            } catch (Throwable ignored) {}
            if ((tickCounter % intervalTicks) != 0) return;
            java.util.Collection<ServerPlayerEntity> targets;
            try {
                var cfg = com.portable.storage.config.ServerConfig.getInstance();
                if (cfg.isEnableOnDemandSync()) {
                    java.util.ArrayList<ServerPlayerEntity> list = new java.util.ArrayList<>();
                    for (java.util.UUID uuid : PlayerViewState.getViewingPlayers()) {
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                        if (p != null) list.add(p);
                    }
                    targets = list;
                } else {
                    targets = server.getPlayerManager().getPlayerList();
                }
            } catch (Throwable t) {
                targets = server.getPlayerManager().getPlayerList();
            }
            for (ServerPlayerEntity p : targets) {
                try {
                    ServerNetworkingHandlers.sendIncrementalSyncOnDemand(p);
                } catch (Throwable ignored) {}
            }
        });
    }
}


