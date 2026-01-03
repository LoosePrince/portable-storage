package com.portablestorage.mixin;

import com.mojang.serialization.Lifecycle;
import com.portablestorage.config.ModConfig;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 跳过实验性功能警告的 Mixin
 * 在 Minecraft 1.21+ 版本中，添加新维度会触发实验性功能警告
 * 此 Mixin 用于跳过该警告，避免每次进入世界时都显示
 */
@Mixin(PrimaryLevelData.class)
public class ExperimentalFeaturesMixin {

    @Inject(method = "worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;", at = @At("HEAD"), cancellable = true)
    private void portablestorage$skipExperimentalWarning(CallbackInfoReturnable<Lifecycle> cir) {
        if (ModConfig.removeExperimentalWarning) {
            // 跳过实验性功能警告，返回稳定的生命周期
            // 这样可以避免每次进入世界时都显示"使用实验性设置的世界不受支持"的警告
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}

