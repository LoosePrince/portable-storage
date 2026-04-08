package com.portablestorage.screen;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class CraftingWarehouseScreen extends AbstractContainerScreen<CraftingWarehouseScreenHandler> {
    private static final Identifier CRAFTING_TABLE_TEXTURE = Identifier
            .withDefaultNamespace("textures/gui/container/crafting_table.png");

    public CraftingWarehouseScreen(CraftingWarehouseScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRAFTING_TABLE_TEXTURE, this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean hasFocus) {
        // 大部分鼠标事件处理由 AbstractContainerScreenMixin 和 WarehouseWidget 处理
        // 这里只处理合成界面特有的逻辑（如果有的话）
        return super.mouseClicked(event, hasFocus);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 大部分键盘事件处理由 AbstractContainerScreenMixin 和 WarehouseWidget 处理
        // 合成界面特有的 ESC 返回背包逻辑
        if (event.key() == 256) { // ESC
            returnToInventoryScreen();
            return true;
        }
        return super.keyPressed(event);
    }

    public void returnToInventoryScreen() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        this.minecraft.player.clientSideCloseContainer();
        this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
    }

    // removed, mouseReleased, mouseDragged, mouseScrolled 等事件处理由
    // AbstractContainerScreenMixin 和 WarehouseWidget 处理

    public int getWarehouseX() {
        return this.leftPos + WarehouseConstants.getWarehouseXOffset();
    }

    public int getWarehouseY() {
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player)
                .getWarehouse(this.minecraft.player.getUUID());
        return this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
    }

    public int getWarehouseWidth() {
        return WarehouseConstants.getWarehouseWidth();
    }

    public int getWarehouseHeight() {
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player)
                .getWarehouse(this.minecraft.player.getUUID());
        if (warehouse.isFolded())
            return WarehouseConstants.WAREHOUSE_FOLDED_HEIGHT;
        return WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
    }
}
