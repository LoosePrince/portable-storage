package com.portablestorage.util;

import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class WarehouseRenderer {
    private static final ResourceLocation WAREHOUSE_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("portablestorage", "textures/gui/icon.png");
    private static final ResourceLocation WAREHOUSE_GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath("portablestorage", "textures/gui/gui.png");
    private static final ResourceLocation WAREHOUSE_SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath("portablestorage", "textures/gui/slot.png");

    public static void renderBackground(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, PlayerWarehouse warehouse, Font font) {
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + rows * WarehouseConstants.SLOT_SIZE;

        if (!warehouse.isFolded()) {
            // 背景九宫格
            drawNinePatch(graphics, WAREHOUSE_GUI_TEXTURE, x, y, WarehouseConstants.WAREHOUSE_WIDTH, warehouseHeight, WarehouseConstants.WAREHOUSE_CORNER_SIZE);
            
            // 搜索框背景和边框
            int sbX = x + WarehouseConstants.SEARCH_BOX_X_OFFSET;
            int sbY = y + WarehouseConstants.SEARCH_BOX_Y_OFFSET;
            graphics.fill(sbX, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BG_COLOR);
            graphics.fill(sbX, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + 1, WarehouseConstants.SEARCH_BOX_BORDER_DARK);
            graphics.fill(sbX, sbY, sbX + 1, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_DARK);
            graphics.fill(sbX, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT - 1, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);
            graphics.fill(sbX + WarehouseConstants.SEARCH_BOX_WIDTH - 1, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);

            // +/- 按钮
            renderPlusMinusButtons(graphics, font, x + WarehouseConstants.PLUS_MINUS_X_OFFSET, y + WarehouseConstants.PLUS_MINUS_Y_OFFSET, mouseX, mouseY);
            
            // 槽位背景
            int slotStartX = x + (WarehouseConstants.SLOT_LOGIC_X - WarehouseConstants.WAREHOUSE_X_OFFSET) + WarehouseConstants.SLOT_VISUAL_OFFSET; 
            int slotStartY = y + (WarehouseConstants.SLOT_LOGIC_Y_BASE - WarehouseConstants.WAREHOUSE_Y_OFFSET) + WarehouseConstants.SLOT_VISUAL_OFFSET;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                    graphics.blit(WAREHOUSE_SLOT_TEXTURE, slotStartX + col * WarehouseConstants.SLOT_SIZE, slotStartY + row * WarehouseConstants.SLOT_SIZE, 0, 0, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE);
                }
            }

            // 滚动条
            renderScrollbar(graphics, x, y, mouseX, mouseY, warehouse);
        }
    }

    public static void renderScrollbar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        int rows = warehouse.getVisibleRows();
        int scrollbarX = x + WarehouseConstants.SCROLLBAR_X_OFFSET; 
        int scrollbarY = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = rows * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
        
        if (scrollbarHeight > 0) {
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, WarehouseConstants.SCROLLBAR_BG_COLOR);
            int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / (double) WarehouseConstants.SLOTS_PER_ROW);
            int thumbHeight = (totalRows <= rows) ? scrollbarHeight : Math.max(10, (int) (scrollbarHeight * ((float) rows / totalRows)));
            int maxOffset = Math.max(0, totalRows - rows);
            int thumbY = scrollbarY + (maxOffset == 0 ? 0 : (warehouse.getScrollOffset() * (scrollbarHeight - thumbHeight) / maxOffset));
            
            boolean hovered = mouseX >= scrollbarX && mouseX <= scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
            int thumbColor = hovered ? WarehouseConstants.SCROLLBAR_THUMB_HOVER_COLOR : WarehouseConstants.SCROLLBAR_THUMB_COLOR;
            
            graphics.fill(scrollbarX, thumbY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
            graphics.fill(scrollbarX - 1, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY, WarehouseConstants.SCROLLBAR_BORDER_LIGHT); 
            graphics.fill(scrollbarX - 1, thumbY, scrollbarX, thumbY + thumbHeight, WarehouseConstants.SCROLLBAR_BORDER_LIGHT); 
            graphics.fill(scrollbarX, thumbY + thumbHeight, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight + 1, WarehouseConstants.SCROLLBAR_BORDER_DARK); 
            graphics.fill(scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight, WarehouseConstants.SCROLLBAR_BORDER_DARK); 
        }
    }

    public static void renderQuantityTexts(GuiGraphics graphics, Font font, int leftPos, int topPos, PlayerWarehouse warehouse) {
        if (warehouse.isFolded()) return;
        
        int startX = leftPos + WarehouseConstants.SLOT_LOGIC_X;
        int startY = topPos + WarehouseConstants.SLOT_LOGIC_Y_BASE;

        for (int i = 0; i < warehouse.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW; i++) {
            long count = warehouse.getRealCount(i);
            if (count > 1) { 
                String countStr = WarehouseUtils.formatCount(count);
                int row = i / WarehouseConstants.SLOTS_PER_ROW;
                int col = i % WarehouseConstants.SLOTS_PER_ROW;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, WarehouseConstants.QUANTITY_TEXT_Z_OFFSET);
                float scale = WarehouseConstants.QUANTITY_TEXT_SCALE;
                int textX = startX + col * WarehouseConstants.SLOT_SIZE + WarehouseConstants.QUANTITY_TEXT_X_RELATIVE - (int)(font.width(countStr) * scale);
                int textY = startY + row * WarehouseConstants.SLOT_SIZE + WarehouseConstants.QUANTITY_TEXT_Y_RELATIVE;
                graphics.pose().translate(textX, textY, 0);
                graphics.pose().scale(scale, scale, 1.0f);
                graphics.drawString(font, countStr, 0, 0, WarehouseConstants.QUANTITY_TEXT_COLOR, true);
                graphics.pose().popPose();
            }
        }
    }

    public static void renderAllTooltips(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        int foldButtonX = leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldButtonY = topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        
        if (mouseX >= foldButtonX && mouseX < foldButtonX + 18 && mouseY >= foldButtonY && mouseY < foldButtonY + 18) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(warehouse.isFolded() ? "gui.portablestorage.button.unfold" : "gui.portablestorage.button.fold"));
            tooltip.add(Component.translatable("gui.portablestorage.button.settings_hint").withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }

        if (warehouse.isFolded()) return;

        int x = leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET;
        int y = topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
        int bx = x + WarehouseConstants.SIDEBAR_X_OFFSET;
        int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

        // 排序模式
        if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y && mouseY < y + 18) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.portablestorage.button.sort_mode"));
            String modeKey = switch (warehouse.getSortMode()) {
                case 0 -> "gui.portablestorage.sort.count";
                case 1 -> "gui.portablestorage.sort.name";
                case 2 -> "gui.portablestorage.sort.id";
                case 3 -> "gui.portablestorage.sort.time";
                default -> "gui.portablestorage.sort.id";
            };
            tooltip.add(Component.translatable("gui.portablestorage.current", Component.translatable(modeKey)).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }

        // 排序顺序
        if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing && mouseY < y + iconSpacing + 18) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.portablestorage.button.sort_order"));
            String orderKey = warehouse.isAscending() ? "gui.portablestorage.order.ascending" : "gui.portablestorage.order.descending";
            tooltip.add(Component.translatable("gui.portablestorage.current", Component.translatable(orderKey)).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }

        // 快速存取
        if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing * 2 && mouseY < y + iconSpacing * 2 + 18) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.portablestorage.button.quick_interaction"));
            String statusKey = warehouse.isQuickInteraction() ? "gui.portablestorage.on" : "gui.portablestorage.off";
            tooltip.add(Component.translatable("gui.portablestorage.current", Component.translatable(statusKey)).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
    }

    public static void drawNinePatch(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int cornerSize) {
        int textureSize = 30;
        int centerSize = textureSize - cornerSize * 2;
        int targetCenterWidth = width - cornerSize * 2;
        int targetCenterHeight = height - cornerSize * 2;
        graphics.blit(texture, x, y, 0, 0, cornerSize, cornerSize, textureSize, textureSize);
        graphics.blit(texture, x + width - cornerSize, y, textureSize - cornerSize, 0, cornerSize, cornerSize, textureSize, textureSize);
        graphics.blit(texture, x, y + height - cornerSize, 0, textureSize - cornerSize, cornerSize, cornerSize, textureSize, textureSize);
        graphics.blit(texture, x + width - cornerSize, y + height - cornerSize, textureSize - cornerSize, textureSize - cornerSize, cornerSize, cornerSize, textureSize, textureSize);
        graphics.blit(texture, x + cornerSize, y, targetCenterWidth, cornerSize, cornerSize, 0, centerSize, cornerSize, textureSize, textureSize);
        graphics.blit(texture, x + cornerSize, y + height - cornerSize, targetCenterWidth, cornerSize, cornerSize, textureSize - cornerSize, centerSize, cornerSize, textureSize, textureSize);
        graphics.blit(texture, x, y + cornerSize, cornerSize, targetCenterHeight, 0, cornerSize, cornerSize, centerSize, textureSize, textureSize);
        graphics.blit(texture, x + width - cornerSize, y + cornerSize, cornerSize, targetCenterHeight, textureSize - cornerSize, cornerSize, cornerSize, centerSize, textureSize, textureSize);
        graphics.blit(texture, x + cornerSize, y + cornerSize, targetCenterWidth, targetCenterHeight, cornerSize, cornerSize, centerSize, centerSize, textureSize, textureSize);
    }

    public static void renderSidebarButtons(GuiGraphics graphics, int leftPos, int topPos, int sidebarX, int sidebarY, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        int foldButtonX = leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldButtonY = topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        
        if (warehouse.isFolded()) {
            renderIconButton(graphics, foldButtonX, foldButtonY, 13, mouseX, mouseY);
        } else {
            renderIconButton(graphics, foldButtonX, foldButtonY, 0, mouseX, mouseY);
            
            int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;
            renderIconButton(graphics, sidebarX, sidebarY, 1 + warehouse.getSortMode(), mouseX, mouseY);
            
            int orderIconIndex = warehouse.isAscending() ? 6 : 5;
            renderIconButton(graphics, sidebarX, sidebarY + iconSpacing, orderIconIndex, mouseX, mouseY);
            renderIconButton(graphics, sidebarX, sidebarY + iconSpacing * 2, 9, mouseX, mouseY);
        }
    }

    public static void renderIconButton(GuiGraphics graphics, int x, int y, int iconIndex, int mouseX, int mouseY) {
        int u = (iconIndex % 5) * 16;
        int v = (iconIndex / 5) * 16;
        graphics.blit(WAREHOUSE_ICON_TEXTURE, x + 1, y + 1, u, v, 16, 16, 80, 48);
    }

    public static void renderPlusMinusButtons(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        renderTinyButton(graphics, font, x, y, "-", mouseX, mouseY);
        renderTinyButton(graphics, font, x + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING, y, "+", mouseX, mouseY);
    }

    public static void renderTinyButton(GuiGraphics graphics, Font font, int x, int y, String text, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + WarehouseConstants.TINY_BUTTON_SIZE && mouseY >= y && mouseY < y + WarehouseConstants.TINY_BUTTON_SIZE;
        int color = hovered ? 0xFFAAAAAA : 0xFF888888;
        
        graphics.fill(x, y, x + WarehouseConstants.TINY_BUTTON_SIZE, y + WarehouseConstants.TINY_BUTTON_SIZE, color);
        graphics.fill(x - 1, y - 1, x + WarehouseConstants.TINY_BUTTON_SIZE, y, 0xFFBBBBBB); 
        graphics.fill(x - 1, y, x, y + WarehouseConstants.TINY_BUTTON_SIZE, 0xFFBBBBBB); 
        graphics.fill(x, y + WarehouseConstants.TINY_BUTTON_SIZE, x + WarehouseConstants.TINY_BUTTON_SIZE + 1, y + WarehouseConstants.TINY_BUTTON_SIZE + 1, 0xFF444444); 
        graphics.fill(x + WarehouseConstants.TINY_BUTTON_SIZE, y - 1, x + WarehouseConstants.TINY_BUTTON_SIZE + 1, y + WarehouseConstants.TINY_BUTTON_SIZE, 0xFF444444); 

        int textX = x + (WarehouseConstants.TINY_BUTTON_SIZE / 2) - font.width(text) / 2 + 1;
        int textY = y + 2;
        graphics.drawString(font, text, textX, textY, 0xFFFFFF, false);
    }
}

