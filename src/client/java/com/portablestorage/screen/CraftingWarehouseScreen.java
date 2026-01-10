package com.portablestorage.screen;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;

public class CraftingWarehouseScreen extends AbstractContainerScreen<CraftingWarehouseScreenHandler> {
    private static final ResourceLocation CRAFTING_TABLE_TEXTURE = new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");

    public CraftingWarehouseScreen(CraftingWarehouseScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageHeight = 166;
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (this.hoveredSlot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            return; // 拦截
        }
        super.renderTooltip(graphics, x, y);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        // 仓库渲染由 Mixin 注入的 WarehouseWidget 处理
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        // 渲染合成台背景纹理
        graphics.blit(CRAFTING_TABLE_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        // 仓库背景和侧边栏按钮由 Mixin 注入的 WarehouseWidget 处理
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 大部分鼠标事件处理由 AbstractContainerScreenMixin 和 WarehouseWidget 处理
        // 这里只处理合成界面特有的逻辑（如果有的话）
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 大部分键盘事件处理由 AbstractContainerScreenMixin 和 WarehouseWidget 处理
        // 合成界面特有的 ESC 返回背包逻辑
        if (keyCode == 256) { // ESC
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // removed, mouseReleased, mouseDragged, mouseScrolled 等事件处理由 AbstractContainerScreenMixin 和 WarehouseWidget 处理

    public int getWarehouseX() {
        return this.leftPos + WarehouseConstants.getWarehouseXOffset();
    }

    public int getWarehouseY() {
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        return this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
    }

    public int getWarehouseWidth() {
        return WarehouseConstants.getWarehouseWidth();
    }

    public int getWarehouseHeight() {
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        if (warehouse.isFolded()) return WarehouseConstants.WAREHOUSE_FOLDED_HEIGHT;
        return WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
    }
}

