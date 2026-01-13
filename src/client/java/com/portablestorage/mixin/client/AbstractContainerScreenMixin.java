package com.portablestorage.mixin.client;

import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.client.gui.WarehouseWidget;
import com.portablestorage.client.handler.TooltipHandler;
import com.portablestorage.handler.WarehouseMenuHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        if (warehouseWidget != null)
            warehouseWidget.init();
    }

    @Inject(method = "removed", at = @At("HEAD"))
    protected void onRemoved(CallbackInfo ci) {
        if (warehouseWidget != null)
            warehouseWidget.removed();
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void onRenderSlot(GuiGraphics graphics, net.minecraft.world.inventory.Slot slot, CallbackInfo ci) {
        if (slot.container instanceof com.portablestorage.component.PlayerWarehouse
                || slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            if (warehouseWidget == null || !warehouseWidget.shouldShow()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null && warehouseWidget.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null && warehouseWidget.mouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null && warehouseWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (warehouseWidget != null && warehouseWidget.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        net.minecraft.world.inventory.Slot hoveredSlot = accessor.portablestorage$getHoveredSlot();
        if (TooltipHandler.handleTooltip(screen, graphics, hoveredSlot, x, y, 
                stack -> net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stack))) {
            ci.cancel();
        }
    }
}
