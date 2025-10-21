package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.event.ItemPickupEventHandler;
import com.portable.storage.item.StorageKeyItem;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 拦截物品拾取，实现仓库钥匙的保护机制
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    
    @Shadow
    public abstract ItemStack getStack();
    
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void onPlayerCollision(PlayerEntity player, CallbackInfo ci) {
        ItemStack stack = getStack();
        
        // 检查是否为仓库钥匙
        if (StorageKeyItem.isStorageKey(stack)) {
            // 检查玩家是否可以拾取
            if (!ItemPickupEventHandler.canPlayerPickup(player, stack)) {
                // 取消拾取
                ci.cancel();
            }
        }
    }
}