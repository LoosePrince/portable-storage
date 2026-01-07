package com.portablestorage.mixin.client;

import com.portablestorage.client.handler.InventoryScreenHandler;
import com.portablestorage.util.WarehouseConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
    private InventoryScreenHandler handler;

    @Unique
    private EditBox searchBox;

    public InventoryScreenMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        if (this.handler == null) {
            this.handler = new InventoryScreenHandler(this);
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInitReturn(CallbackInfo ci) {
        if (!handler.shouldShowWarehouse()) return;

        int sbX = this.leftPos + WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbY = this.topPos + WarehouseConstants.getWarehouseYOffset(0) + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbW = WarehouseConstants.SEARCH_BOX_WIDTH - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        int sbH = WarehouseConstants.SEARCH_BOX_HEIGHT - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        
        this.searchBox = new EditBox(this.font, sbX, sbY, sbW, sbH, Component.literal(""));
        this.searchBox.setResponder(text -> handler.setPendingSearchText(text));
        this.searchBox.setEditable(true);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.portablestorage.search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        
        handler.init(this.searchBox);
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public void removed() {
        if (handler != null) handler.onRemoved();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (handler != null && handler.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Inject(method = "renderBg", at = @At("HEAD"))
    protected void onRenderBgHead(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (handler != null && handler.shouldShowWarehouse()) {
            this.imageHeight = WarehouseConstants.VANILLA_INVENTORY_HEIGHT;
        }
    }

    @Inject(method = "renderBg", at = @At("RETURN"))
    protected void onRenderBgReturn(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (handler != null) handler.onRenderBgReturn(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handler != null && handler.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (handler != null) handler.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (handler != null && handler.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handler != null && handler.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void renderWarehouseContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (handler != null) handler.render(graphics, mouseX, mouseY);
    }
}
