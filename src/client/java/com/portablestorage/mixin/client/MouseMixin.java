package com.portablestorage.mixin.client;

import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.client.gui.WarehouseWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {

    @Shadow private double xpos;
    @Shadow private double ypos;

    @Inject(method = "onScroll", at = @At("TAIL"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof WarehouseScreen ws) {
            WarehouseWidget widget = ws.portablestorage$getWarehouseWidget();
            if (widget != null) {
                double mouseX = this.xpos * (double) Minecraft.getInstance().getWindow().getGuiScaledWidth() / (double) Minecraft.getInstance().getWindow().getWidth();
                double mouseY = this.ypos * (double) Minecraft.getInstance().getWindow().getGuiScaledHeight() / (double) Minecraft.getInstance().getWindow().getHeight();
                
                if (widget.mouseScrolled(mouseX, mouseY, horizontal, vertical)) {
                    ci.cancel();
                }
            }
        }
    }
}
