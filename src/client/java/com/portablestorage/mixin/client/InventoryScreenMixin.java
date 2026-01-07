package com.portablestorage.mixin.client;

import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    public InventoryScreenMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void hideRecipeBookButton(CallbackInfo ci) {
        if (ModConfig.hideRecipeBook) {
            this.children().removeIf(child -> {
                if (child instanceof AbstractWidget btn) {
                    if (btn.getWidth() == 20 && (btn.getHeight() == 18 || btn.getHeight() == 19)) {
                        return true;
                    }
                }
                return false;
            });
            ((ScreenAccessor) this).portablestorage$getRenderables().removeIf(renderable -> {
                if (renderable instanceof AbstractWidget btn) {
                    if (btn.getWidth() == 20 && (btn.getHeight() == 18 || btn.getHeight() == 19)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    /**
     * 在 renderBg 之后注入，绘制仓库背景（在原版槽位高亮之前）
     * 这样原版的槽位高亮会覆盖在仓库背景之上
     */
    @Inject(method = "renderBg", at = @At("RETURN"))
    private void onRenderBgReturn(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        WarehouseScreen screen = (WarehouseScreen) this;
        var warehouseWidget = screen.portablestorage$getWarehouseWidget();
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderBackground(graphics, mouseX, mouseY);
        }
    }

    /**
     * 在 render 方法返回前注入，绘制覆盖层和文本（在原版槽位高亮之后）
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        WarehouseScreen screen = (WarehouseScreen) this;
        var warehouseWidget = screen.portablestorage$getWarehouseWidget();
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderOverlays(graphics, mouseX, mouseY, partialTick);
        }
    }
}
