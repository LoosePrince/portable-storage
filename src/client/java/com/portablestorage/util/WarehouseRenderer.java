package com.portablestorage.util;

import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class WarehouseRenderer {
    private static final ResourceLocation WAREHOUSE_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("portablestorage", "textures/gui/icon.png");

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

