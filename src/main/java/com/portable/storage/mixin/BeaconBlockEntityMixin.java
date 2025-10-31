package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.entity.RiftAvatarEntity;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * 在信标发放效果后，额外给范围内的复制体追加相同效果（复制体会转发到所属玩家）。
 * 这里无法直接访问私有的 primary/secondary，采用与原版一致的计算方式读取当前层级与半径。
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    @Inject(method = "applyPlayerEffects", at = @At("TAIL"))
    private static void portableStorage$applyAvatarEffects(World world, BlockPos pos, int level, net.minecraft.entity.effect.StatusEffect primary, net.minecraft.entity.effect.StatusEffect secondary, CallbackInfo ci) {
        if (world == null || world.isClient) return;
        // 半径计算与原版一致：level * 10 + 10
        int range = level * 10 + 10;
        // 垂直方向使用对称扩展，避免只向正向拉伸导致部分高度未覆盖从而刷新不稳定
        Box box = new Box(pos).expand(range).expand(0.0, world.getHeight(), 0.0);
        try {
            // 与原版一致的放大等级与副效果规则：
            // - 四层时且主副相同，主效果为II（amplifier=1），不再单独应用副效果
            // - 否则主效果为I（amplifier=0）；若副效果存在且不同于主效果，则额外应用一次副效果
            if (primary != null) {
                boolean isLevelFour = level >= 4;
                boolean sameAsSecondary = secondary != null && secondary == primary;
                int amplifier = (isLevelFour && sameAsSecondary) ? 1 : 0;
                int duration = (9 + level * 2) * 20; // 与原版一致：9s + 每层+2s

                StatusEffectInstance primaryEffect = new StatusEffectInstance(primary, duration, amplifier, true, true, true);
                for (RiftAvatarEntity avatar : world.getEntitiesByClass(RiftAvatarEntity.class, box, e -> true)) {
                    avatar.addStatusEffect(primaryEffect);
                }
            }
            if (secondary != null && secondary != primary) {
                int duration = (9 + level * 2) * 20; // 与原版一致
                StatusEffectInstance secondaryEffect = new StatusEffectInstance(secondary, duration, 0, true, true, true);
                for (RiftAvatarEntity avatar : world.getEntitiesByClass(RiftAvatarEntity.class, box, e -> true)) {
                    avatar.addStatusEffect(secondaryEffect);
                }
            }
        } catch (Throwable ignored) {}
    }
}


