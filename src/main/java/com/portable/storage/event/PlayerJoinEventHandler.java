package com.portable.storage.event;

import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.sync.StorageSyncManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 处理玩家加入和离开游戏的事件
 */
public class PlayerJoinEventHandler {
    
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            // 玩家加入时发送启用状态同步
            ServerNetworkingHandlers.sendEnablementSync(player);
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            // 玩家离开时清理同步状态
            StorageSyncManager.cleanupPlayer(player.getUuid());
            // 清理按需同步状态
            com.portable.storage.sync.PlayerViewState.cleanupPlayer(player.getUuid());
            com.portable.storage.sync.ChangeAccumulator.cleanupPlayer(player.getUuid());
            // 清空垃圾桶槽位（客户端缓存）
            com.portable.storage.storage.UpgradeInventory upgrades = com.portable.storage.player.PlayerStorageService.getUpgradeInventory(player);
            if (upgrades.isTrashSlotActive()) {
                upgrades.setStack(10, net.minecraft.item.ItemStack.EMPTY);
            }
        });
    }
}
