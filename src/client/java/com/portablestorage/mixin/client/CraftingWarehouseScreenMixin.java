package com.portablestorage.mixin.client;

import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.screen.CraftingWarehouseScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingWarehouseScreen.class)
public abstract class CraftingWarehouseScreenMixin {
    // 注意：CraftingWarehouseScreen 继承自 AbstractContainerScreen，
    // AbstractContainerScreenMixin 已经处理了 WarehouseScreen 接口的实现
    // 这里只需要添加 renderBg 和 render 的注入点

    /**
     * 在 renderBg 之后注入，绘制仓库背景（在原版槽位高亮之前）
     */
    @Inject(method = "renderBg", at = @At("RETURN"))
    private void onRenderBgReturn(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // 通过 AbstractContainerScreenMixin 获取 WarehouseWidget
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        WarehouseScreen warehouseScreen = (WarehouseScreen) screen;
        var widget = warehouseScreen.portablestorage$getWarehouseWidget();
        if (widget != null && widget.shouldShow()) {
            widget.renderBackground(graphics, mouseX, mouseY);
        }
    }

    /**
     * 在 render 方法返回前注入，绘制覆盖层和文本（在原版槽位高亮之后）
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // 通过 AbstractContainerScreenMixin 获取 WarehouseWidget
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        WarehouseScreen warehouseScreen = (WarehouseScreen) screen;
        var widget = warehouseScreen.portablestorage$getWarehouseWidget();
        if (widget != null && widget.shouldShow()) {
            widget.renderOverlays(graphics, mouseX, mouseY, partialTick);
        }
    }
}
