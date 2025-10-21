package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.UpgradeInventory;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * 处理箭矢命中目标时施加光灵效果
 */
@Mixin(ArrowEntity.class)
public abstract class ArrowEntityMixin extends PersistentProjectileEntity {

    protected ArrowEntityMixin(EntityType<? extends PersistentProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "onHit", at = @At("HEAD"))
    private void portableStorage$applySpectralEffect(LivingEntity target, CallbackInfo ci) {
        if (this.getWorld().isClient) return;
        if (!(this.getOwner() instanceof ServerPlayerEntity player)) return;

        // 检查箭矢类型是否为普通箭
        ItemStack arrowStack = this.asItemStack();
        if (!arrowStack.isOf(Items.ARROW)) return;

        // 检查玩家是否有光灵箭升级
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        if (!upgrades.isSpectralArrowUpgradeActive()) return;

        // 施加光灵效果10秒
        StatusEffectInstance spectralEffect = new StatusEffectInstance(
            StatusEffects.GLOWING, 
            10 * 20, // 10秒 = 200 ticks
            0, // 等级0
            false, // 不是环境效果
            true, // 显示粒子
            true // 显示图标
        );
        
        target.addStatusEffect(spectralEffect);
    }
}
