package com.portablestorage.mixin;

import com.portablestorage.entity.RiftAvatarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    /**
     * 优化：对齐原版信标的效果半径和计算逻辑
     */
    @Inject(method = "applyEffects", at = @At("TAIL"))
    private static void portablestorage$applyAvatarEffects(Level level, BlockPos pos, int levels, Holder<MobEffect> primary, Holder<MobEffect> secondary, CallbackInfo ci) {
        if (level == null || level.isClientSide || primary == null) return;
        
        int range = levels * 10 + 10;
        // 使用与原版完全一致的 AABB 计算方式
        AABB box = (new AABB(pos)).inflate((double)range).expandTowards(0.0, (double)level.getHeight(), 0.0);
        
        try {
            int duration = (9 + levels * 2) * 20;
            int primaryAmplifier = (levels >= 4 && primary.equals(secondary)) ? 1 : 0;
            MobEffectInstance primaryEffect = new MobEffectInstance(primary, duration, primaryAmplifier, true, true, true);
            
            // 仅在范围内存在复制体时才执行后续逻辑
            List<RiftAvatarEntity> avatars = level.getEntitiesOfClass(RiftAvatarEntity.class, box);
            if (avatars.isEmpty()) return;

            for (RiftAvatarEntity avatar : avatars) {
                avatar.addEffect(primaryEffect);
                
                if (secondary != null && !secondary.equals(primary)) {
                    MobEffectInstance secondaryEffect = new MobEffectInstance(secondary, duration, 0, true, true, true);
                    avatar.addEffect(secondaryEffect);
                }
            }
        } catch (Throwable ignored) {}
    }
}
