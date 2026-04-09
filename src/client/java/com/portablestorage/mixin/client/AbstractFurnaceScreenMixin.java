package com.portablestorage.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portablestorage.client.gui.WarehouseScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;

@Mixin(AbstractFurnaceScreen.class)
public abstract class AbstractFurnaceScreenMixin {

    @Inject(method = "extractBackground", at = @At("RETURN"))
    private void onExtractBackgroundReturn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        WarehouseScreen screen = (WarehouseScreen) this;
        var warehouseWidget = screen.portablestorage$getWarehouseWidget();
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderBackground(graphics, mouseX, mouseY);
        }
    }
}
