package com.portablestorage.screen;

import com.portablestorage.config.YACLConfig;
import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.*;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseRenderer;
import com.portablestorage.util.WarehouseSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.resources.ResourceLocation;

public class CraftingWarehouseScreen extends AbstractContainerScreen<CraftingWarehouseScreenHandler> {
    private static final ResourceLocation CRAFTING_TABLE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private EditBox searchBox;
    private boolean isDraggingScrollbar = false;

    // 自定义折叠按钮在合成界面中的位置（第三行右侧）
    private static final int CRAFT_FOLD_X = 84;
    private static final int CRAFT_FOLD_Y = 53;

    public CraftingWarehouseScreen(CraftingWarehouseScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        
        // 1. 支持 offsetInventory 属性
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        int yOffset = ModConfig.offsetInventory ? (warehouse.isFolded() ? WarehouseConstants.OFFSET_FOLDED : WarehouseConstants.OFFSET_BASE + rows * WarehouseConstants.OFFSET_PER_ROW) : 0;
        
        super.init();
        if (yOffset > 0) {
            this.topPos -= yOffset;
        }

        // 搜索框
        int sbX = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbY = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbW = WarehouseConstants.SEARCH_BOX_WIDTH - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        int sbH = WarehouseConstants.SEARCH_BOX_HEIGHT - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        
        this.searchBox = new EditBox(this.font, sbX, sbY, sbW, sbH, Component.literal(""));
        this.searchBox.setResponder(text -> ClientPlayNetworking.send(new SearchPayload(text)));
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setValue(warehouse.getSearchText());
        this.searchBox.visible = !warehouse.isFolded();
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        this.renderTooltip(graphics, mouseX, mouseY);
        
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        
        // 特殊处理合成界面的 Tooltip 偏移
        renderCraftingTooltips(graphics, mouseX, mouseY, warehouse);
        
        WarehouseRenderer.renderQuantityTexts(graphics, this.font, this.leftPos, this.topPos, warehouse);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blit(CRAFTING_TABLE_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        int x = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET;
        int y = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
        
        WarehouseRenderer.renderBackground(graphics, x, y, mouseX, mouseY, warehouse, this.font);
        
        // 渲染侧边栏按钮（传入自定义折叠按钮位置）
        WarehouseRenderer.renderSidebarButtons(graphics, this.leftPos + CRAFT_FOLD_X, this.topPos + CRAFT_FOLD_Y, x + WarehouseConstants.SIDEBAR_X_OFFSET, y, mouseX, mouseY, warehouse);
    }

    private void renderCraftingTooltips(GuiGraphics graphics, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        // 使用合成界面特有的折叠按钮判定
        if (mouseX >= this.leftPos + CRAFT_FOLD_X && mouseX < this.leftPos + CRAFT_FOLD_X + 18 && mouseY >= this.topPos + CRAFT_FOLD_Y && mouseY < this.topPos + CRAFT_FOLD_Y + 18) {
            WarehouseRenderer.renderFoldTooltip(graphics, this.font, mouseX, mouseY, warehouse);
            return;
        }
        
        if (!warehouse.isFolded()) {
            WarehouseRenderer.renderSidebarTooltips(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY, warehouse);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        
        // 处理 Shift+点击
        if (button == 0 && hasShiftDown() && warehouse.isQuickInteraction() && !warehouse.isFolded()) {
            Slot clickedSlot = null;
            for (Slot slot : this.menu.slots) {
                int slotX = this.leftPos + slot.x;
                int slotY = this.topPos + slot.y;
                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    clickedSlot = slot;
                    break;
                }
            }
            if (clickedSlot != null && clickedSlot.container instanceof PlayerWarehouse) {
                ClientPlayNetworking.send(new QuickTransferPayload(clickedSlot.index));
                return true;
            }
        }

        // 1. 处理自定义位置的折叠按钮点击
        if (mouseX >= this.leftPos + CRAFT_FOLD_X && mouseX < this.leftPos + CRAFT_FOLD_X + 18 && mouseY >= this.topPos + CRAFT_FOLD_Y && mouseY < this.topPos + CRAFT_FOLD_Y + 18) {
            if (button == 2) { // 中键
                this.minecraft.setScreen(YACLConfig.create(this));
                return true;
            }
            if (button == 0) { // 左键
                boolean newFolded = !warehouse.isFolded();
                warehouse.setFolded(newFolded);
                ClientPlayNetworking.send(new UpdateSettingsPayload(WarehouseSetting.FOLD, newFolded ? 1 : 0));
                this.minecraft.setScreen(new CraftingWarehouseScreen(this.menu, this.minecraft.player.getInventory(), this.title));
                return true;
            }
        }

        if (!warehouse.isFolded()) {
            int x = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET;
            int y = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
            int bx = x + WarehouseConstants.SIDEBAR_X_OFFSET;
            int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y && mouseY < y + 18) {
                ClientPlayNetworking.send(new UpdateSettingsPayload(WarehouseSetting.SORT_MODE, (warehouse.getSortMode() + 1) % 4));
                return true;
            }
            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing && mouseY < y + iconSpacing + 18) {
                ClientPlayNetworking.send(new UpdateSettingsPayload(WarehouseSetting.SORT_ORDER, warehouse.isAscending() ? 0 : 1));
                return true;
            }
            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing * 2 && mouseY < y + iconSpacing * 2 + 18) {
                ClientPlayNetworking.send(new UpdateSettingsPayload(WarehouseSetting.QUICK_INTERACTION, warehouse.isQuickInteraction() ? 0 : 1));
                return true;
            }
            
            // 2. 合成按钮变为返回背包
            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing * 3 && mouseY < y + iconSpacing * 3 + 18) {
                this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
                return true;
            }

            int pmX = x + WarehouseConstants.PLUS_MINUS_X_OFFSET;
            int pmY = y + WarehouseConstants.PLUS_MINUS_Y_OFFSET;
            if (mouseX >= pmX && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
                ClientPlayNetworking.send(new ChangeRowsPayload(-1));
                this.minecraft.setScreen(new CraftingWarehouseScreen(this.menu, this.minecraft.player.getInventory(), this.title));
                return true;
            }
            if (mouseX >= pmX + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE * 2 + WarehouseConstants.TINY_BUTTON_SPACING && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
                ClientPlayNetworking.send(new ChangeRowsPayload(1));
                this.minecraft.setScreen(new CraftingWarehouseScreen(this.menu, this.minecraft.player.getInventory(), this.title));
                return true;
            }

            int sx = x + WarehouseConstants.SCROLLBAR_X_OFFSET;
            int sy = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
            int sh = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
            if (mouseX >= sx && mouseX <= sx + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= sy && mouseY <= sy + sh) {
                this.isDraggingScrollbar = true;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 3. 支持 ESC 返回背包
        if (keyCode == 256) { // ESC
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScrollbar) {
            PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
            int scrollbarY = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET + WarehouseConstants.SCROLLBAR_Y_OFFSET;
            int scrollbarHeight = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
            
            int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / 9.0);
            int visibleRows = warehouse.getVisibleRows();
            int maxOffset = Math.max(0, totalRows - visibleRows);
            
            if (maxOffset > 0) {
                int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalRows)));
                double relativeY = Math.clamp(mouseY - scrollbarY - thumbHeight / 2.0, 0, scrollbarHeight - thumbHeight);
                int newOffset = (int) Math.round((relativeY * maxOffset) / (scrollbarHeight - thumbHeight));
                if (newOffset != warehouse.getScrollOffset()) ClientPlayNetworking.send(new ScrollPayload(warehouse.getScrollOffset() - newOffset));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        if (warehouse.isFolded()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        int warehouseX = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET;
        int warehouseY = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
        if (mouseX >= warehouseX && mouseX < warehouseX + WarehouseConstants.WAREHOUSE_WIDTH && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            ClientPlayNetworking.send(new ScrollPayload((int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}

