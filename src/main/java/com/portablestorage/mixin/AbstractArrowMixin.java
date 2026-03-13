package com.portablestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.upgrade.SpectralArrowUpgrade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin extends Projectile {
    protected AbstractArrowMixin(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void onHitEntity(EntityHitResult entityHitResult, CallbackInfo ci) {
        Entity shooter = this.getOwner();
        if (shooter instanceof Player player && !player.level().isClientSide()) {
            PlayerWarehouse warehouse = ModComponents.getWarehouse(((ServerLevel) player.level()).getServer(),
                    player.getUUID());
            if (warehouse != null && warehouse.isEnabled()
                    && !warehouse.getUpgrade(SpectralArrowUpgrade.ID).isEmpty()) {
                Entity hitEntity = entityHitResult.getEntity();
                if (hitEntity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200)); // 10s = 200 ticks
                }
            }
        }
    }
}
