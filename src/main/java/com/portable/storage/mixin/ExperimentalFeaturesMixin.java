package com.portable.storage.mixin;

import com.mojang.serialization.Lifecycle;
import com.portable.storage.PortableStorage;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 跳过实验性功能警告的 Mixin
 * 在 Minecraft 1.20.5+ 版本中，添加新维度会触发实验性功能警告
 * 此 Mixin 用于跳过该警告，避免每次进入世界时都显示
 */
@Mixin(LevelProperties.class)
public class ExperimentalFeaturesMixin {

    @Inject(method = "getLifecycle()Lcom/mojang/serialization/Lifecycle;", at = @At("HEAD"), cancellable = true)
    private void portableStorage$skipExperimentalWarning(CallbackInfoReturnable<Lifecycle> cir) {
        // 跳过实验性功能警告，返回稳定的生命周期
        // 这样可以避免每次进入世界时都显示"使用实验性设置的世界不受支持"的警告
        PortableStorage.LOGGER.debug("跳过实验性功能警告 - 返回稳定生命周期");
        cir.setReturnValue(Lifecycle.stable());
    }
}
