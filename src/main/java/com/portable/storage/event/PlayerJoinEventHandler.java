package com.portable.storage.event;

import com.portable.storage.net.ServerNetworkingHandlers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 处理玩家加入游戏的事件
 */
public class PlayerJoinEventHandler {
    
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            // 玩家加入时发送启用状态同步
            ServerNetworkingHandlers.sendEnablementSync(player);
        });
    }
}
