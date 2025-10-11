package com.portable.storage.util;

import com.portable.storage.item.StorageKeyItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 仓库钥匙保护机制
 * 防止仓库钥匙被非拥有者拾取、销毁或使用
 */
public class StorageKeyProtection {
    
    private StorageKeyProtection() {}
    
    /**
     * 检查玩家是否可以拾取仓库钥匙
     */
    public static boolean canPickup(PlayerEntity player, ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return true; // 不是仓库钥匙，正常拾取
        }
        
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return StorageKeyItem.isOwner(serverPlayer, stack);
        }
        
        return false;
    }
    
    /**
     * 检查玩家是否可以使用仓库钥匙
     */
    public static boolean canUse(PlayerEntity player, ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return true; // 不是仓库钥匙，正常使用
        }
        
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return StorageKeyItem.isOwner(serverPlayer, stack);
        }
        
        return false;
    }
    
    /**
     * 检查仓库钥匙是否可以被销毁
     */
    public static boolean canDestroy(PlayerEntity player, ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return true; // 不是仓库钥匙，正常销毁
        }
        
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return StorageKeyItem.isOwner(serverPlayer, stack);
        }
        
        return false;
    }
    
    /**
     * 处理非拥有者尝试拾取仓库钥匙的情况
     */
    public static void handleUnauthorizedPickup(ServerPlayerEntity player, ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return;
        }
    }
    
    /**
     * 处理非拥有者尝试使用仓库钥匙的情况
     */
    public static void handleUnauthorizedUse(ServerPlayerEntity player, ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return;
        }
        
        String ownerName = getOwnerName(stack);
        if (ownerName != null) {
            player.sendMessage(Text.translatable("portable_storage.message.key_belongs_to", ownerName)
                    .formatted(Formatting.RED), false);
        } else {
            player.sendMessage(Text.translatable("portable_storage.message.key_unauthorized")
                    .formatted(Formatting.RED), false);
        }
    }
    
    /**
     * 处理非拥有者尝试销毁仓库钥匙的情况
     */
    public static void handleUnauthorizedDestroy(ServerPlayerEntity player, ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return;
        }
        
        player.sendMessage(Text.translatable("portable_storage.message.key_indestructible")
                .formatted(Formatting.RED), false);
    }
    
    /**
     * 防止仓库钥匙被销毁
     * 参考 looseprinces-tool 的灵魂绑定物品保护机制
     */
    public static boolean preventDestroy(ItemEntity itemEntity, DamageSource source) {
        ItemStack stack = itemEntity.getStack();
        
        // 检查是否为仓库钥匙
        if (!StorageKeyItem.isStorageKey(stack)) {
            return false; // 不是仓库钥匙，不保护
        }
        
        // 岩浆/火焰保护
        if (source.isOf(DamageTypes.LAVA) || 
            source.isOf(DamageTypes.IN_FIRE) || 
            source.isOf(DamageTypes.ON_FIRE)) {
            return true;
        }
        
        // 虚空保护：不销毁，抬升到安全位置 - 参考 SoulBindingService
        if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
            com.portable.storage.PortableStorage.LOGGER.info("Storage key void protection triggered at position ({}, {}, {})", 
                itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
            
            double x = itemEntity.getX();
            double z = itemEntity.getZ();
            double safeY = Math.max(itemEntity.getWorld().getBottomY() + 1, 1);
            
            // 设置位置到安全高度
            itemEntity.setPosition(x, safeY, z);
            
            // 设置向上的速度
            itemEntity.setVelocity(0, 0.4, 0);
            
            com.portable.storage.PortableStorage.LOGGER.info("Storage key teleported to safe position ({}, {}, {})", x, safeY, z);
            return true;
        }
        
        // 仙人掌/甜浆果灌木保护
        if (source.isOf(DamageTypes.CACTUS) || 
            source.isOf(DamageTypes.SWEET_BERRY_BUSH)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取仓库钥匙的拥有者名称
     */
    private static String getOwnerName(ItemStack stack) {
        if (!StorageKeyItem.isStorageKey(stack)) {
            return null;
        }
        
        try {
            var customData = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            if (customData != null) {
                var nbtCompound = customData.copyNbt();
                if (nbtCompound.contains("storage_key_owner_name")) {
                    return nbtCompound.getString("storage_key_owner_name");
                }
            }
        } catch (Exception ignored) {
        }
        
        return null;
    }
}
