package com.portablestorage.mixin.client;

import com.portablestorage.client.gui.WarehouseWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    @Unique
    private WarehouseWidget warehouseWidget;

    public InventoryScreenMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        if (this.warehouseWidget == null) {
            this.warehouseWidget = new WarehouseWidget(this);
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInitReturn(CallbackInfo ci) {
        if (warehouseWidget != null) warehouseWidget.init();
    }

    @Override
    public void removed() {
        if (warehouseWidget != null) warehouseWidget.removed();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (warehouseWidget != null && warehouseWidget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Inject(method = "renderBg", at = @At("HEAD"))
    protected void onRenderBgHead(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // Handled by widget if needed, but here we can still do quick adjustments
    }

    @Inject(method = "renderBg", at = @At("RETURN"))
    protected void onRenderBgReturn(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (warehouseWidget != null) warehouseWidget.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (warehouseWidget != null && warehouseWidget.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (warehouseWidget != null) warehouseWidget.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (warehouseWidget != null && warehouseWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (warehouseWidget != null && warehouseWidget.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
