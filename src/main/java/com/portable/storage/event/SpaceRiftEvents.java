package com.portable.storage.event;

import com.portable.storage.world.SpaceRiftManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class SpaceRiftEvents {
    private SpaceRiftEvents() {}

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // 保持返回点
        });

        ServerTickEvents.END_SERVER_TICK.register(SpaceRiftEvents::onEndServerTick);

        // 玩家加入：强制加载其所属裂隙区块
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                java.util.UUID id = handler.player.getUuid();
                com.portable.storage.world.SpaceRiftManager.setPlayerPlotForced(server, id, true);
            } catch (Throwable ignored) {}
        });

        // 玩家离开：取消强制加载
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                java.util.UUID id = handler.player.getUuid();
                com.portable.storage.world.SpaceRiftManager.setPlayerPlotForced(server, id, false);
            } catch (Throwable ignored) {}
        });
    }

    private static void onEndServerTick(MinecraftServer server) {
        ServerWorld rift = SpaceRiftManager.getWorld(server);
        if (rift == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() != rift) continue;
            // 每tick确保个人边界已应用
            SpaceRiftManager.applyPersonalBorder(player);
            // 记录玩家在裂隙内的最后位置
            SpaceRiftManager.updateLastRiftPos(player);
            BlockPos pos = player.getBlockPos();
            if (!SpaceRiftManager.isInsideOwnPlot(player, pos)) {
                var origin = SpaceRiftManager.ensureAllocatedPlot(server, player.getUuid());
                BlockPos center = SpaceRiftManager.getPlotCenterBlock(origin);
                player.teleport(rift, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, player.getYaw(), player.getPitch());
            }
        }
    }
}


