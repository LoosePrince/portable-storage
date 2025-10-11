package com.portable.storage.mixin;

import com.portable.storage.util.StorageKeyProtection;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仓库钥匙的防销毁 Mixin
 * 参考 looseprinces-tool 的灵魂绑定物品保护机制
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityStorageKeyMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void portableStorage$onTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        
        // 检查是否为仓库钥匙
        if (com.portable.storage.item.StorageKeyItem.isStorageKey(self.getStack())) {
            // 虚空上浮保护 - 参考 SoulBindingService.applyLevel2Tick
            int bottomY = self.getWorld().getBottomY();
            if (self.getY() < bottomY + 1) {
                com.portable.storage.PortableStorage.LOGGER.info("Storage key void protection triggered in tick: position ({}, {}, {}) -> ({}, {}, {})", 
                    self.getX(), self.getY(), self.getZ(), self.getX(), bottomY + 1, self.getZ());
                
                self.setPosition(self.getX(), bottomY + 1, self.getZ());
                self.setVelocity(self.getVelocity().x, Math.max(self.getVelocity().y, 0.4), self.getVelocity().z);
            }
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void portableStorage$preventDestroy(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        
        // 检查是否为仓库钥匙
        if (com.portable.storage.item.StorageKeyItem.isStorageKey(self.getStack())) {
            com.portable.storage.PortableStorage.LOGGER.info("Storage key damage event: source={}, amount={}, position=({}, {}, {})", 
                source.getName(), amount, self.getX(), self.getY(), self.getZ());
        }
        
        boolean blocked = StorageKeyProtection.preventDestroy(self, source);
        if (blocked) {
            com.portable.storage.PortableStorage.LOGGER.info("Storage key damage blocked by protection");
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
