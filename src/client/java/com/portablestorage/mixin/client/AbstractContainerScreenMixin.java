package com.portablestorage.mixin.client;

import com.portablestorage.client.handler.TooltipHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow protected Slot hoveredSlot;
    @Shadow protected abstract List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack);

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (TooltipHandler.handleTooltip((AbstractContainerScreen<?>)(Object)this, graphics, this.hoveredSlot, x, y, this::getTooltipFromContainerItem)) {
            ci.cancel();
        }
    }
}
