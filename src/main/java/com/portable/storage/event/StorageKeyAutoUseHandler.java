package com.portable.storage.event;

import com.portable.storage.item.StorageKeyItem;
import com.portable.storage.player.PlayerStorageAccess;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 仓库钥匙自动使用处理器
 * 当仓库钥匙在玩家背包中时自动使用并消耗
 */
public class StorageKeyAutoUseHandler {
    
    private static final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private static final long CHECK_INTERVAL = 20L; // 每20tick（1秒）检查一次
    
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long currentTime = server.getTicks();
            
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                Long lastCheck = lastCheckTime.get(playerId);
                
                // 检查是否需要检查这个玩家
                if (lastCheck == null || currentTime - lastCheck >= CHECK_INTERVAL) {
                    checkAndAutoUseStorageKey(player);
                    lastCheckTime.put(playerId, currentTime);
                }
            }
        });
    }
    
    /**
     * 检查并自动使用仓库钥匙
     */
    private static void checkAndAutoUseStorageKey(ServerPlayerEntity player) {
        // 检查玩家背包中是否有仓库钥匙
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            
            if (StorageKeyItem.isStorageKey(stack)) {
                // 检查是否为钥匙的拥有者
                if (StorageKeyItem.isOwner(player, stack)) {
                    // 自动使用仓库钥匙，无视激活状态
                    autoUseStorageKey(player, stack, i);
                    return; // 一次只处理一个钥匙
                }
            }
        }
    }
    
    /**
     * 自动使用仓库钥匙
     */
    private static void autoUseStorageKey(ServerPlayerEntity player, ItemStack stack, int slotIndex) {
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        
        // 检查玩家是否已经激活仓库
        if (access.portableStorage$isStorageEnabled()) {
            // 仓库已激活，仍然消耗钥匙（根据需求）
            consumeStorageKey(player, stack, slotIndex);
            
            // 发送消息通知钥匙被消耗
            player.sendMessage(Text.translatable("portable_storage.message.storage_key_consumed_already_enabled")
                    .formatted(Formatting.YELLOW), false);
        } else {
            // 仓库未激活，激活仓库并消耗钥匙
            access.portableStorage$setStorageEnabled(true);
            consumeStorageKey(player, stack, slotIndex);
            
            // 发送成功消息
            player.sendMessage(Text.translatable("portable_storage.message.storage_reactivated")
                    .formatted(Formatting.GREEN), false);
        }
    }
    
    /**
     * 消耗仓库钥匙
     */
    private static void consumeStorageKey(ServerPlayerEntity player, ItemStack stack, int slotIndex) {
        if (stack.getCount() > 1) {
            stack.decrement(1);
        } else {
            player.getInventory().setStack(slotIndex, ItemStack.EMPTY);
        }
    }
}
