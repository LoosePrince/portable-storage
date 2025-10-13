package com.portable.storage.event;

import com.portable.storage.item.StorageKeyItem;
import com.portable.storage.util.StorageKeyProtection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 处理物品拾取事件，实现仓库钥匙的保护机制
 */
public class ItemPickupEventHandler {
    
    public static void register() {
        // 这个类主要用于提供静态方法，实际的物品实体监听通过 Mixin 实现
    }
    
    
    /**
     * 检查玩家是否可以拾取物品
     * 这个方法会被其他系统调用来验证拾取权限
     */
    public static boolean canPlayerPickup(PlayerEntity player, ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return true; // 不是仓库钥匙，正常拾取
        }
        
        if (player instanceof ServerPlayerEntity serverPlayer) {
            boolean canPickup = StorageKeyProtection.canPickup(serverPlayer, stack);
            
            if (!canPickup) {
                // 发送提示消息
                StorageKeyProtection.handleUnauthorizedPickup(serverPlayer, stack);
            }
            
            return canPickup;
        }
        
        return false;
    }
}
