package com.portablestorage.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
import net.minecraft.world.inventory.AbstractContainerMenu;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> implements WarehouseScreen {

    @Unique
    private WarehouseWidget warehouseWidget;

    @Override
    public WarehouseWidget portablestorage$getWarehouseWidget() {
        return warehouseWidget;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        // 排除创造模式背包界面
        if ((Object) this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
            return;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        // 仅在适配过的界面注入
        if (!WarehouseMenuHandler.isAdaptedMenu(screen.getMenu()))
            return;

        // 确保客户端菜单已有槽位后再执行 UI 逻辑
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
    private void onRenderSlot(GuiGraphicsExtractor graphics, net.minecraft.world.inventory.Slot slot, int x, int y,
            CallbackInfo ci) {
        if (slot.container instanceof com.portablestorage.component.PlayerWarehouse
                || slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            if (warehouseWidget == null || !warehouseWidget.shouldShow()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean consumed, CallbackInfoReturnable<Boolean> cir) {
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
    private void onMouseDragged(MouseButtonEvent event, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null
                && warehouseWidget.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null && warehouseWidget.keyPressed(event.key(), event.scancode(), event.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        if (warehouseWidget != null) {
            warehouseWidget.renderPreTooltipOverlays(graphics, x, y);
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        net.minecraft.world.inventory.Slot hoveredSlot = accessor.portablestorage$getHoveredSlot();
        if (TooltipHandler.handleTooltip(screen, graphics, hoveredSlot, x, y,
                stack -> net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stack))) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onExtractRenderStateHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        // InventoryScreen needs to draw warehouse background after its own background pass.
        if ((Object) this instanceof InventoryScreen) {
            return;
        }
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderBackground(graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderOverlays(graphics, mouseX, mouseY, partialTick);
        }
    }
}
