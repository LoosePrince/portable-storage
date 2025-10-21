package com.portable.storage.event;

import com.portable.storage.config.ServerConfig;
import com.portable.storage.world.SpaceRiftManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
        
        // 服务器启动后检查所有在线玩家
        ServerLifecycleEvents.SERVER_STARTED.register(SpaceRiftEvents::onServerStarted);

        // 玩家加入：强制加载其所属裂隙区块，并检查是否需要送回原位置
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                java.util.UUID id = handler.player.getUuid();
                SpaceRiftManager.setPlayerPlotForced(server, id, true);
                
                // 检查裂隙功能是否启用，如果禁用且玩家在裂隙中则送回原位置
                if (!ServerConfig.getInstance().isEnableRiftFeature()) {
                    if (handler.player.getWorld().getRegistryKey() == SpaceRiftManager.DIMENSION_KEY) {
                        // 延迟执行，确保玩家完全加入后再处理
                        server.execute(() -> {
                            SpaceRiftManager.safelyKickPlayerFromRift(handler.player);
                        });
                    }
                }
            } catch (Throwable ignored) {}
        });

        // 玩家离开：取消强制加载
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                java.util.UUID id = handler.player.getUuid();
                SpaceRiftManager.setPlayerPlotForced(server, id, false);
            } catch (Throwable ignored) {}
        });
    }

    private static void onEndServerTick(MinecraftServer server) {
        ServerWorld rift = SpaceRiftManager.getWorld(server);
        if (rift == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() != rift) continue;
            
            // 检查玩家是否为观察者模式或创造模式
            boolean isSpectatorOrCreative = player.isSpectator() || player.isCreative();
            
            // 每tick确保个人边界已应用（观察者和创造者除外）
            if (!isSpectatorOrCreative) {
                SpaceRiftManager.applyPersonalBorder(player);
            }
            
            // 记录玩家在裂隙内的最后位置
            SpaceRiftManager.updateLastRiftPos(player);
            
            // 只有非观察者和非创造者才会被传送回自己的地块
            if (!isSpectatorOrCreative) {
                BlockPos pos = player.getBlockPos();
                if (!SpaceRiftManager.isInsideOwnPlot(player, pos)) {
                    var origin = SpaceRiftManager.ensureAllocatedPlot(server, player.getUuid());
                    BlockPos center = SpaceRiftManager.getPlotCenterBlock(origin);
                    player.teleport(rift, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, player.getYaw(), player.getPitch());
                }
            }
        }
    }
    
    private static void onServerStarted(MinecraftServer server) {
        // 延迟执行，确保服务器完全启动后再检查
        server.execute(() -> {
            try {
                // 服务器启动后检查所有在线玩家，如果裂隙功能被禁用则踢出在裂隙中的玩家
                if (!ServerConfig.getInstance().isEnableRiftFeature()) {
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        if (player.getWorld().getRegistryKey() == SpaceRiftManager.DIMENSION_KEY) {
                            SpaceRiftManager.safelyKickPlayerFromRift(player);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }
}


