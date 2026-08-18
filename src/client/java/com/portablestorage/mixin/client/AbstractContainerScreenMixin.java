package com.portablestorage.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portablestorage.PortableStorageClient;
import com.portablestorage.client.gui.QuickToolClientState;
import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.client.gui.WarehouseWidget;
import com.portablestorage.client.handler.TooltipHandler;
import com.portablestorage.handler.WarehouseMenuHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements WarehouseScreen {

    @Unique
    private WarehouseWidget warehouseWidget;

    @Override
    public WarehouseWidget portablestorage$getWarehouseWidget() {
        return warehouseWidget;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
            return;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!WarehouseMenuHandler.isAdaptedMenu(screen.getMenu()))
            return;

        WarehouseMenuHandler.injectWarehouseSlots(screen.getMenu(), Minecraft.getInstance().player);

        if (this.warehouseWidget == null) {
            this.warehouseWidget = new WarehouseWidget(screen);
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInitReturn(CallbackInfo ci) {
        if (warehouseWidget != null) {
            warehouseWidget.init();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    protected void onRemoved(CallbackInfo ci) {
        if (warehouseWidget != null)
            warehouseWidget.removed();
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void onRenderSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot.container instanceof com.portablestorage.component.PlayerWarehouse
                || slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            if (warehouseWidget == null || !warehouseWidget.shouldShow()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null && warehouseWidget.mouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null && warehouseWidget.mouseReleased(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null
                && warehouseWidget.mouseDragged(event.x(), event.y(), event.button(), dx, dy)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (QuickToolClientState.handleNumberKey(event.key())) {
            cir.setReturnValue(true);
            return;
        }
        if (PortableStorageClient.matchesToggleWarehouseFoldKey(event)
                && PortableStorageClient.tryToggleWarehouseFold(Minecraft.getInstance())) {
            cir.setReturnValue(true);
            return;
        }
        if (warehouseWidget != null && warehouseWidget.keyPressed(event.key(), event.scancode(), event.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractContents", at = @At("RETURN"))
    private void onExtractContentsReturn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderItemOverlaysBeforeCarried(graphics);
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (warehouseWidget != null) {
            warehouseWidget.renderTooltipOverlays(graphics, mouseX, mouseY);
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        Slot hoveredSlot = accessor.portablestorage$getHoveredSlot();
        if (TooltipHandler.handleTooltip(screen, graphics, hoveredSlot, mouseX, mouseY,
                stack -> net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stack))) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onExtractRenderStateHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) {
            return;
        }
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderBackground(graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderOverlays(graphics, mouseX, mouseY, a);
        }
    }
}