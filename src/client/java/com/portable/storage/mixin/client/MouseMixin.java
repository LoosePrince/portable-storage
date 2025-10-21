package com.portable.storage.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.client.ClientStorageState;
import com.portable.storage.client.ui.StorageUIComponent;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private double x;
    @Shadow private double y;

    @Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void portableStorage$onMouseScroll(long windowHandle, double horizontal, double vertical, CallbackInfo ci) {
        if (!ClientStorageState.isStorageEnabled()) return;
        StorageUIComponent ui = StorageUIComponent.getCurrentInstance();
        Screen screen = this.client.currentScreen;
        if (ui == null || screen == null) return;

        Window window = this.client.getWindow();
        if (window == null) return;

        double scaledX = this.x * (double)window.getScaledWidth() / (double)window.getWidth();
        double scaledY = this.y * (double)window.getScaledHeight() / (double)window.getHeight();

        if (ui.mouseScrolled(scaledX, scaledY, horizontal, vertical)) {
            ci.cancel();
        }
    }
}


