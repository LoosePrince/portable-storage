package com.portablestorage.util;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class WarehouseRenderer {
    private static final ResourceLocation WAREHOUSE_ICON_TEXTURE = PortableStorage.id("textures/gui/icon.png");
    private static final ResourceLocation WAREHOUSE_GUI_TEXTURE = PortableStorage.id("textures/gui/gui.png");
    private static final ResourceLocation WAREHOUSE_SLOT_TEXTURE = PortableStorage.id("textures/gui/slot.png");

    public static void renderBackground(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, PlayerWarehouse warehouse, Font font) {
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + rows * WarehouseConstants.SLOT_SIZE;

        if (!warehouse.isFolded()) {
            // 绘制统一的仓库背景 (宽度为 214)
            drawNinePatch(graphics, WAREHOUSE_GUI_TEXTURE, x, y, WarehouseConstants.WAREHOUSE_WIDTH, warehouseHeight, WarehouseConstants.WAREHOUSE_CORNER_SIZE);
            
            // 绘制升级槽位和图标
            int upgradeSlotX = x + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X + WarehouseConstants.SLOT_VISUAL_OFFSET;
            int upgradeSlotY = y + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y + WarehouseConstants.SLOT_VISUAL_OFFSET;
            List<com.portablestorage.upgrade.UpgradeType> allUpgrades = com.portablestorage.upgrade.UpgradeRegistry.getAllUpgrades();
            int upgradeOffset = warehouse.getUpgradeScrollOffset();

            for (int i = 0; i < rows; i++) {
                int slotY = upgradeSlotY + i * WarehouseConstants.SLOT_SIZE;
                // 绘制槽位背景
                graphics.blit(WAREHOUSE_SLOT_TEXTURE, upgradeSlotX, slotY, 0, 0, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE);
                
                // 如果当前索引有对应的升级类型，且槽位为空，绘制图标
                int upgradeIndex = i + upgradeOffset;
                if (upgradeIndex < allUpgrades.size()) {
                    com.portablestorage.upgrade.UpgradeType type = allUpgrades.get(upgradeIndex);
                    if (warehouse.getUpgrade(type.getId()).isEmpty()) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(0, 0, 100);
                        // 设置半透明色（Alpha 0.5）
                        graphics.setColor(1.0f, 1.0f, 1.0f, 0.5f);
                        graphics.blit(type.getIcon(), upgradeSlotX + 1, slotY + 1, 0, 0, 16, 16, 16, 16);
                        // 还原颜色，防止影响后续渲染
                        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                        graphics.pose().popPose();
                    }
                }
            }

            // 绘制搜索框背景
            int sbX = x + WarehouseConstants.SEARCH_BOX_X_OFFSET;
            int sbY = y + WarehouseConstants.SEARCH_BOX_Y_OFFSET;
            graphics.fill(sbX, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BG_COLOR);
            graphics.fill(sbX, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + 1, WarehouseConstants.SEARCH_BOX_BORDER_DARK);
            graphics.fill(sbX, sbY, sbX + 1, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_DARK);
            graphics.fill(sbX, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT - 1, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);
            graphics.fill(sbX + WarehouseConstants.SEARCH_BOX_WIDTH - 1, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);

            renderPlusMinusButtons(graphics, font, x + WarehouseConstants.PLUS_MINUS_X_OFFSET, y + WarehouseConstants.PLUS_MINUS_Y_OFFSET, mouseX, mouseY);
            
            int slotStartX = x + WarehouseConstants.SLOT_RELATIVE_X + WarehouseConstants.SLOT_VISUAL_OFFSET; 
            int slotStartY = y + WarehouseConstants.SLOT_RELATIVE_Y + WarehouseConstants.SLOT_VISUAL_OFFSET;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                    graphics.blit(WAREHOUSE_SLOT_TEXTURE, slotStartX + col * WarehouseConstants.SLOT_SIZE, slotStartY + row * WarehouseConstants.SLOT_SIZE, 0, 0, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE);
                }
            }

            renderScrollbar(graphics, x, y, mouseX, mouseY, warehouse);
            renderUpgradeScrollbar(graphics, x, y, mouseX, mouseY, warehouse);
        }
    }

    public static void renderUpgradeScrollbar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        int rows = warehouse.getVisibleRows();
        int scrollbarX = x + WarehouseConstants.UPGRADE_SCROLLBAR_X_OFFSET; 
        int scrollbarY = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = rows * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
        
        int totalUpgrades = com.portablestorage.upgrade.UpgradeRegistry.getUpgradeCount();
        if (scrollbarHeight > 0 && totalUpgrades > rows) {
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, WarehouseConstants.SCROLLBAR_BG_COLOR);
            int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) rows / totalUpgrades)));
            int maxOffset = totalUpgrades - rows;
            int thumbY = scrollbarY + (warehouse.getUpgradeScrollOffset() * (scrollbarHeight - thumbHeight) / maxOffset);
            
            boolean hovered = mouseX >= scrollbarX && mouseX <= scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
            int thumbColor = hovered ? WarehouseConstants.SCROLLBAR_THUMB_HOVER_COLOR : WarehouseConstants.SCROLLBAR_THUMB_COLOR;
            
            graphics.fill(scrollbarX, thumbY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
            graphics.fill(scrollbarX - 1, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY, WarehouseConstants.SCROLLBAR_BORDER_LIGHT); 
            graphics.fill(scrollbarX - 1, thumbY, scrollbarX, thumbY + thumbHeight, WarehouseConstants.SCROLLBAR_BORDER_LIGHT); 
            graphics.fill(scrollbarX, thumbY + thumbHeight, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight + 1, WarehouseConstants.SCROLLBAR_BORDER_DARK); 
            graphics.fill(scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight, WarehouseConstants.SCROLLBAR_BORDER_DARK); 
        }
    }

    public static void renderSidebarButtons(GuiGraphics graphics, int foldX, int foldY, int sidebarX, int sidebarY, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        boolean showShortcuts = com.portablestorage.config.ModConfig.showSmallIcons;
        boolean horizontal = com.portablestorage.config.ModConfig.storagePosition.isHorizontal();
        
        if (warehouse.isFolded()) {
            renderIconButton(graphics, foldX, foldY, WarehouseConstants.ICON_FOLDED, mouseX, mouseY);
        } else {
            renderIconButton(graphics, foldX, foldY, WarehouseConstants.ICON_UNFOLDED, mouseX, mouseY);
            
            int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;
            
            if (showShortcuts) {
                // 排序模式 (1-4)
                renderIconButton(graphics, sidebarX, sidebarY, WarehouseConstants.ICON_SORT_MODE_BASE + warehouse.getSortMode(), mouseX, mouseY);
                
                // 排序顺序 (5/6)
                int orderIconIndex = warehouse.isAscending() ? WarehouseConstants.ICON_ORDER_ASC : WarehouseConstants.ICON_ORDER_DESC;
                int ox = horizontal ? sidebarX + iconSpacing : sidebarX;
                int oy = horizontal ? sidebarY : sidebarY + iconSpacing;
                renderIconButton(graphics, ox, oy, orderIconIndex, mouseX, mouseY);
                
                // 快速交互 (9)
                int qx = horizontal ? sidebarX + iconSpacing * 2 : sidebarX;
                int qy = horizontal ? sidebarY : sidebarY + iconSpacing * 2;
                renderIconButton(graphics, qx, qy, WarehouseConstants.ICON_QUICK_INTERACTION, mouseX, mouseY);
                
                // 智能折叠 (10/11)
                int sx = horizontal ? sidebarX + iconSpacing * 3 : sidebarX;
                int sy = horizontal ? sidebarY : sidebarY + iconSpacing * 3;
                renderIconButton(graphics, sx, sy, warehouse.isSmartCollapse() ? WarehouseConstants.ICON_SMART_COLLAPSE_ON : WarehouseConstants.ICON_SMART_COLLAPSE_OFF, mouseX, mouseY);
                
                // 合成补充 (7)
                int rx = horizontal ? sidebarX + iconSpacing * 4 : sidebarX;
                int ry = horizontal ? sidebarY : sidebarY + iconSpacing * 4;
                renderIconButton(graphics, rx, ry, WarehouseConstants.ICON_CRAFT_REFILL, mouseX, mouseY);
            }

            // 合成台图标 (14)
            int cx = horizontal ? (sidebarX + (showShortcuts ? iconSpacing * 5 : 0)) : sidebarX;
            int cy = horizontal ? sidebarY : (sidebarY + (showShortcuts ? iconSpacing * 5 : 0));
            renderIconButton(graphics, cx, cy, WarehouseConstants.ICON_CRAFTING_TABLE, mouseX, mouseY);
        }
    }

    public static void renderSidebarTooltips(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        boolean showShortcuts = com.portablestorage.config.ModConfig.showSmallIcons;
        boolean horizontal = com.portablestorage.config.ModConfig.storagePosition.isHorizontal();
        int x = leftPos + WarehouseConstants.getWarehouseXOffset();
        int y = topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        int bx = x + WarehouseConstants.getSidebarXOffset();
        int by = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows());
        int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

        if (showShortcuts) {
            // 排序模式
            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18) {
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
            int ox = horizontal ? bx + iconSpacing : bx;
            int oy = horizontal ? by : by + iconSpacing;
            if (mouseX >= ox && mouseX < ox + 18 && mouseY >= oy && mouseY < oy + 18) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.portablestorage.button.sort_order"));
                String orderKey = warehouse.isAscending() ? "gui.portablestorage.order.ascending" : "gui.portablestorage.order.descending";
                tooltip.add(Component.translatable("gui.portablestorage.current", Component.translatable(orderKey)).withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                return;
            }

            // 快速交互
            int qx = horizontal ? bx + iconSpacing * 2 : bx;
            int qy = horizontal ? by : by + iconSpacing * 2;
            if (mouseX >= qx && mouseX < qx + 18 && mouseY >= qy && mouseY < qy + 18) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.portablestorage.button.quick_interaction"));
                String statusKey = warehouse.isQuickInteraction() ? "gui.portablestorage.on" : "gui.portablestorage.off";
                tooltip.add(Component.translatable("gui.portablestorage.current", Component.translatable(statusKey)).withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                return;
            }

            // 智能折叠
            int sx = horizontal ? bx + iconSpacing * 3 : bx;
            int sy = horizontal ? by : by + iconSpacing * 3;
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.portablestorage.button.smart_collapse"));
                String statusKey = warehouse.isSmartCollapse() ? "gui.portablestorage.on" : "gui.portablestorage.off";
                tooltip.add(Component.translatable("gui.portablestorage.current", Component.translatable(statusKey)).withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                return;
            }

            // 合成补充
            int rx = horizontal ? bx + iconSpacing * 4 : bx;
            int ry = horizontal ? by : by + iconSpacing * 4;
            if (mouseX >= rx && mouseX < rx + 18 && mouseY >= ry && mouseY < ry + 18) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.portablestorage.button.craft_refill"));
                String statusKey = warehouse.isCraftRefill() ? "gui.portablestorage.on" : "gui.portablestorage.off";
                tooltip.add(Component.translatable("gui.portablestorage.current", Component.translatable(statusKey)).withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                return;
            }
        }

        // 合成台图标
        int craftingX = horizontal ? (bx + (showShortcuts ? iconSpacing * 5 : 0)) : bx;
        int craftingY = horizontal ? by : (by + (showShortcuts ? iconSpacing * 5 : 0));
        if (mouseX >= craftingX && mouseX < craftingX + 18 && mouseY >= craftingY && mouseY < craftingY + 18) {
            boolean isCrafting = net.minecraft.client.Minecraft.getInstance().screen instanceof com.portablestorage.screen.CraftingWarehouseScreen;
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(isCrafting ? "gui.portablestorage.button.back" : "gui.portablestorage.button.open_crafting"));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
    }

    public static void renderFoldTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(warehouse.isFolded() ? "gui.portablestorage.button.unfold" : "gui.portablestorage.button.fold"));
        tooltip.add(Component.translatable("gui.portablestorage.button.settings_hint").withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    public static void renderAllTooltips(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        int foldButtonX = leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldButtonY = topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        
        if (mouseX >= foldButtonX && mouseX < foldButtonX + 18 && mouseY >= foldButtonY && mouseY < foldButtonY + 18) {
            renderFoldTooltip(graphics, font, mouseX, mouseY, warehouse);
            return;
        }

        if (warehouse.isFolded()) return;
        
        // 渲染升级槽位提示
        renderUpgradeTooltips(graphics, font, leftPos, topPos, mouseX, mouseY, warehouse);
        
        renderSidebarTooltips(graphics, font, leftPos, topPos, mouseX, mouseY, warehouse);
    }

    public static void renderUpgradeTooltips(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        int x = leftPos + WarehouseConstants.getWarehouseXOffset();
        int y = topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        int upgradeSlotX = x + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X;
        int upgradeSlotY = y + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y;
        int rows = warehouse.getVisibleRows();
        
        List<com.portablestorage.upgrade.UpgradeType> allUpgrades = com.portablestorage.upgrade.UpgradeRegistry.getAllUpgrades();
        int upgradeOffset = warehouse.getUpgradeScrollOffset();

        for (int i = 0; i < rows; i++) {
            int slotY = upgradeSlotY + i * WarehouseConstants.SLOT_SIZE;
            if (mouseX >= upgradeSlotX && mouseX < upgradeSlotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                int upgradeIndex = i + upgradeOffset;
                if (upgradeIndex < allUpgrades.size()) {
                    com.portablestorage.upgrade.UpgradeType type = allUpgrades.get(upgradeIndex);
                    ItemStack stack = warehouse.getUpgrade(type.getId());
                    
                    List<Component> finalTooltip = new ArrayList<>();
                    if (!stack.isEmpty()) {
                        // 如果有物品，先获取物品的原版提示
                        finalTooltip.addAll(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(net.minecraft.client.Minecraft.getInstance(), stack));
                        finalTooltip.add(Component.literal(" ")); // 分隔符
                    }
                    
                    // 添加升级自定义提示
                    finalTooltip.addAll(type.getTooltip(warehouse, stack));
                    
                    if (!finalTooltip.isEmpty()) {
                        graphics.renderComponentTooltip(font, finalTooltip, mouseX, mouseY);
                    }
                }
                break;
            }
        }
    }

    public static void renderScrollbar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, PlayerWarehouse warehouse) {
        int rows = warehouse.getVisibleRows();
        int scrollbarX = x + WarehouseConstants.SCROLLBAR_X_OFFSET; 
        int scrollbarY = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = rows * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
        
        if (scrollbarHeight > 0) {
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, WarehouseConstants.SCROLLBAR_BG_COLOR);
            int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / 9.0);
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
        int startX = leftPos + WarehouseConstants.getSlotLogicX();
        int startY = topPos + WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows());

        for (int i = 0; i < warehouse.getVisibleRows() * 9; i++) {
            long count = warehouse.getRealCount(i);
            if (count > 1) { 
                String countStr = WarehouseUtils.formatCount(count);
                int row = i / 9;
                int col = i % 9;
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

    public static void drawNinePatch(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int cornerSize) {
        int textureSize = WarehouseConstants.GUI_TEXTURE_SIZE;
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

    public static void renderIconButton(GuiGraphics graphics, int x, int y, int iconIndex, int mouseX, int mouseY) {
        int u = (iconIndex % 5) * WarehouseConstants.ICON_SIZE;
        int v = (iconIndex / 5) * WarehouseConstants.ICON_SIZE;
        graphics.blit(WAREHOUSE_ICON_TEXTURE, x + 1, y + 1, u, v, WarehouseConstants.ICON_SIZE, WarehouseConstants.ICON_SIZE, WarehouseConstants.ICON_TEXTURE_WIDTH, WarehouseConstants.ICON_TEXTURE_HEIGHT);
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
