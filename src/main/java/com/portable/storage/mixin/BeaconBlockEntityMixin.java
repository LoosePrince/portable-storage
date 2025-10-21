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
    private static void portableStorage$applyAvatarEffects(World world, BlockPos pos, int level, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> primary, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> secondary, CallbackInfo ci) {
        if (world == null || world.isClient) return;
        // 半径计算与原版一致：level * 10 + 10
        int range = level * 10 + 10;
        Box box = new Box(pos).expand(range).stretch(0.0, world.getHeight(), 0.0);
        try {
            // 使用实际的主副效果
            if (primary != null) {
                StatusEffectInstance primaryEffect = new StatusEffectInstance(primary, 180, 0, true, true, true);
                for (RiftAvatarEntity avatar : world.getEntitiesByClass(RiftAvatarEntity.class, box, e -> true)) {
                    avatar.addStatusEffect(primaryEffect);
                }
            }
            if (secondary != null) {
                StatusEffectInstance secondaryEffect = new StatusEffectInstance(secondary, 180, 0, true, true, true);
                for (RiftAvatarEntity avatar : world.getEntitiesByClass(RiftAvatarEntity.class, box, e -> true)) {
                    avatar.addStatusEffect(secondaryEffect);
                }
            }
        } catch (Throwable ignored) {}
    }
}


