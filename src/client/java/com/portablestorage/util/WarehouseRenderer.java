package com.portablestorage.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 仓库界面渲染器
 * 负责绘制仓库背景、槽位、按钮、滚动条等 UI 元素
 */
public class WarehouseRenderer {
        private static final Identifier WAREHOUSE_ICON_TEXTURE = PortableStorage.id("textures/gui/icon.png");
        private static final Identifier WAREHOUSE_GUI_TEXTURE = PortableStorage.id("textures/gui/gui.png");
        private static final Identifier WAREHOUSE_SLOT_TEXTURE = PortableStorage.id("textures/gui/slot.png");
        private static final Identifier DEFAULT_STEVE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft",
                        "textures/entity/steve.png");

        /**
         * 渲染仓库背景
         */
        public static void renderBackground(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                        PlayerWarehouse warehouse, Font font) {
                int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
                int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + rows * WarehouseConstants.SLOT_SIZE;

                if (!warehouse.isFolded()) {
                        // 绘制统一的仓库背景
                        drawNinePatch(graphics, WAREHOUSE_GUI_TEXTURE, x, y, WarehouseConstants.getWarehouseWidth(),
                                        warehouseHeight, WarehouseConstants.WAREHOUSE_CORNER_SIZE);

                        // 绘制升级槽位和图标
                        int upgradeColumnWidth = WarehouseConstants.getUpgradeColumnWidth();
                        if (upgradeColumnWidth > 0) {
                                renderSharingStatus(graphics, x, y, warehouse);
                                int upgradeSlotX = x + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X
                                                + WarehouseConstants.SLOT_VISUAL_OFFSET;
                                int upgradeSlotY = y + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y
                                                + WarehouseConstants.SLOT_VISUAL_OFFSET;
                                List<com.portablestorage.upgrade.UpgradeType> allUpgrades = com.portablestorage.upgrade.UpgradeRegistry
                                                .getAllUpgrades();
                                int upgradeOffset = warehouse.getUpgradeScrollOffset();

                                for (int i = 0; i < rows; i++) {
                                        int upgradeIndex = i + upgradeOffset;
                                        // 仅当索引对应的升级已注册时才渲染
                                        if (upgradeIndex < allUpgrades.size()) {
                                                int slotY = upgradeSlotY + i * WarehouseConstants.SLOT_SIZE;
                                                // 绘制槽位背景
                                                blitRegion(graphics, WAREHOUSE_SLOT_TEXTURE, upgradeSlotX, slotY, 0, 0,
                                                                WarehouseConstants.SLOT_SIZE,
                                                                WarehouseConstants.SLOT_SIZE,
                                                                WarehouseConstants.SLOT_SIZE,
                                                                WarehouseConstants.SLOT_SIZE,
                                                                WarehouseConstants.SLOT_SIZE,
                                                                WarehouseConstants.SLOT_SIZE);

                                                com.portablestorage.upgrade.UpgradeType type = allUpgrades
                                                                .get(upgradeIndex);
                                                if (warehouse.getUpgrade(type.getId()).isEmpty()) {
                                                        ItemStack iconStack = type.getIconStack();
                                                        if (!iconStack.isEmpty()) {
                                                                graphics.renderFakeItem(iconStack, upgradeSlotX + 1,
                                                                                slotY + 1);
                                                        } else {
                                                                Identifier icon = type.getIcon();
                                                                if (icon != null) {
                                                                        blitRegion(graphics, icon, upgradeSlotX + 1,
                                                                                        slotY + 1, 0, 0, 16, 16, 16,
                                                                                        16, 16, 16);
                                                                }
                                                        }
                                                        // 叠加白色半透明遮罩，覆盖物品图标
                                                        graphics.fill(upgradeSlotX + 1, slotY + 1, upgradeSlotX + 17,
                                                                        slotY + 17,
                                                                        WarehouseConstants.MASK_WHITE);
                                                }
                                        }
                                }
                        }

                        // 绘制搜索框背景
                        int sbX = x + WarehouseConstants.getSearchBoxXOffset();
                        int sbY = y + WarehouseConstants.SEARCH_BOX_Y_OFFSET;
                        graphics.fill(sbX, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH,
                                        sbY + WarehouseConstants.SEARCH_BOX_HEIGHT,
                                        WarehouseConstants.SEARCH_BOX_BG_COLOR);
                        graphics.fill(sbX, sbY, sbX + WarehouseConstants.SEARCH_BOX_WIDTH, sbY + 1,
                                        WarehouseConstants.SEARCH_BOX_BORDER_DARK);
                        graphics.fill(sbX, sbY, sbX + 1, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT,
                                        WarehouseConstants.SEARCH_BOX_BORDER_DARK);
                        graphics.fill(sbX, sbY + WarehouseConstants.SEARCH_BOX_HEIGHT - 1,
                                        sbX + WarehouseConstants.SEARCH_BOX_WIDTH,
                                        sbY + WarehouseConstants.SEARCH_BOX_HEIGHT,
                                        WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);
                        graphics.fill(sbX + WarehouseConstants.SEARCH_BOX_WIDTH - 1, sbY,
                                        sbX + WarehouseConstants.SEARCH_BOX_WIDTH,
                                        sbY + WarehouseConstants.SEARCH_BOX_HEIGHT,
                                        WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);

                        renderPlusMinusButtons(graphics, font, x + WarehouseConstants.getPlusMinusXOffset(),
                                        y + WarehouseConstants.PLUS_MINUS_Y_OFFSET, mouseX, mouseY);

                        int slotStartX = x + WarehouseConstants.getSlotRelativeX()
                                        + WarehouseConstants.SLOT_VISUAL_OFFSET;
                        int slotStartY = y + WarehouseConstants.SLOT_RELATIVE_Y + WarehouseConstants.SLOT_VISUAL_OFFSET;
                        for (int row = 0; row < rows; row++) {
                                for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                                        blitRegion(graphics, WAREHOUSE_SLOT_TEXTURE,
                                                        slotStartX + col * WarehouseConstants.SLOT_SIZE,
                                                        slotStartY + row * WarehouseConstants.SLOT_SIZE, 0, 0,
                                                        WarehouseConstants.SLOT_SIZE,
                                                        WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE,
                                                        WarehouseConstants.SLOT_SIZE,
                                                        WarehouseConstants.SLOT_SIZE,
                                                        WarehouseConstants.SLOT_SIZE);
                                }
                        }

                        renderScrollbar(graphics, x, y, mouseX, mouseY, warehouse);
                        renderUpgradeScrollbar(graphics, x, y, mouseX, mouseY, warehouse);
                }
        }

        public static void renderUpgradeScrollbar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                        PlayerWarehouse warehouse) {
                int rows = warehouse.getVisibleRows();
                int scrollbarX = x + WarehouseConstants.UPGRADE_SCROLLBAR_X_OFFSET;
                int scrollbarY = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
                int scrollbarHeight = rows * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;

                int totalUpgrades = com.portablestorage.upgrade.UpgradeRegistry.getUpgradeCount();
                if (scrollbarHeight > 0 && totalUpgrades > rows) {
                        graphics.fill(scrollbarX, scrollbarY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH,
                                        scrollbarY + scrollbarHeight, WarehouseConstants.SCROLLBAR_BG_COLOR);
                        int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) rows / totalUpgrades)));
                        int maxOffset = totalUpgrades - rows;
                        int thumbY = scrollbarY
                                        + (warehouse.getUpgradeScrollOffset() * (scrollbarHeight - thumbHeight)
                                                        / maxOffset);

                        boolean hovered = mouseX >= scrollbarX
                                        && mouseX <= scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH
                                        && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
                        int thumbColor = hovered ? WarehouseConstants.SCROLLBAR_THUMB_HOVER_COLOR
                                        : WarehouseConstants.SCROLLBAR_THUMB_COLOR;

                        graphics.fill(scrollbarX, thumbY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH,
                                        thumbY + thumbHeight,
                                        thumbColor);
                        graphics.fill(scrollbarX - 1, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH,
                                        thumbY,
                                        WarehouseConstants.SCROLLBAR_BORDER_LIGHT);
                        graphics.fill(scrollbarX - 1, thumbY, scrollbarX, thumbY + thumbHeight,
                                        WarehouseConstants.SCROLLBAR_BORDER_LIGHT);
                        graphics.fill(scrollbarX, thumbY + thumbHeight,
                                        scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1,
                                        thumbY + thumbHeight + 1, WarehouseConstants.SCROLLBAR_BORDER_DARK);
                        graphics.fill(scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY - 1,
                                        scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight,
                                        WarehouseConstants.SCROLLBAR_BORDER_DARK);
                }
        }

        public static void renderSidebarButtons(GuiGraphics graphics, int foldX, int foldY, int sidebarX, int sidebarY,
                        int mouseX, int mouseY, PlayerWarehouse warehouse, boolean indentSidebar) {
                boolean showShortcuts = com.portablestorage.config.ModConfig.showSmallIcons;
                boolean horizontal = com.portablestorage.config.ModConfig.storagePosition.isHorizontal();

                if (warehouse.isFolded()) {
                        renderIconButton(graphics, foldX, foldY, WarehouseConstants.ICON_FOLDED, mouseX, mouseY);
                } else {
                        renderIconButton(graphics, foldX, foldY, WarehouseConstants.ICON_UNFOLDED, mouseX, mouseY);

                        int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE
                                        + WarehouseConstants.SIDEBAR_BUTTON_SPACING;
                        int currentSidebarX = sidebarX;
                        int currentSidebarY = sidebarY;

                        // 如果折叠按钮占用了侧边栏第一个位置，我们需要偏移后续按钮
                        if (indentSidebar) {
                                if (horizontal)
                                        currentSidebarX += iconSpacing;
                                else
                                        currentSidebarY += iconSpacing;
                        }

                        if (showShortcuts) {
                                // 排序模式 (1-4)
                                renderIconButton(graphics, currentSidebarX, currentSidebarY,
                                                WarehouseConstants.ICON_SORT_MODE_BASE + warehouse.getSortMode(),
                                                mouseX, mouseY);

                                // 排序顺序 (5/6)
                                int orderIconIndex = warehouse.isAscending() ? WarehouseConstants.ICON_ORDER_ASC
                                                : WarehouseConstants.ICON_ORDER_DESC;
                                int ox = horizontal ? currentSidebarX + iconSpacing : currentSidebarX;
                                int oy = horizontal ? currentSidebarY : currentSidebarY + iconSpacing;
                                renderIconButton(graphics, ox, oy, orderIconIndex, mouseX, mouseY);

                                // 快速交互 (9)
                                int qx = horizontal ? currentSidebarX + iconSpacing * 2 : currentSidebarX;
                                int qy = horizontal ? currentSidebarY : currentSidebarY + iconSpacing * 2;
                                renderIconButton(graphics, qx, qy, WarehouseConstants.ICON_QUICK_INTERACTION, mouseX,
                                                mouseY);

                                // 智能折叠 (10/11)
                                int sx = horizontal ? currentSidebarX + iconSpacing * 3 : currentSidebarX;
                                int sy = horizontal ? currentSidebarY : currentSidebarY + iconSpacing * 3;
                                renderIconButton(graphics, sx, sy,
                                                warehouse.isSmartCollapse() ? WarehouseConstants.ICON_SMART_COLLAPSE_ON
                                                                : WarehouseConstants.ICON_SMART_COLLAPSE_OFF,
                                                mouseX, mouseY);

                                // 合成补充 (7)
                                int rx = horizontal ? currentSidebarX + iconSpacing * 4 : currentSidebarX;
                                int ry = horizontal ? currentSidebarY : currentSidebarY + iconSpacing * 4;
                                renderIconButton(graphics, rx, ry, WarehouseConstants.ICON_CRAFT_REFILL, mouseX,
                                                mouseY);
                        }

                        // 合成台图标 (14)
                        if (!warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
                                int cx = horizontal ? (currentSidebarX + (showShortcuts ? iconSpacing * 5 : 0))
                                                : currentSidebarX;
                                int cy = horizontal ? currentSidebarY
                                                : (currentSidebarY + (showShortcuts ? iconSpacing * 5 : 0));
                                renderIconButton(graphics, cx, cy, WarehouseConstants.ICON_CRAFTING_TABLE, mouseX,
                                                mouseY);
                        }
                }
        }

        public static void renderSidebarTooltips(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX,
                        int mouseY, PlayerWarehouse warehouse, int imageHeight, boolean indentSidebar) {
                boolean showShortcuts = com.portablestorage.config.ModConfig.showSmallIcons;
                boolean horizontal = com.portablestorage.config.ModConfig.storagePosition.isHorizontal();
                int x = leftPos + WarehouseConstants.getWarehouseXOffset();
                int y = topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight);
                int bx = x + WarehouseConstants.getSidebarXOffset();
                int by = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows(), imageHeight);
                int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

                if (indentSidebar) {
                        if (horizontal)
                                bx += iconSpacing;
                        else
                                by += iconSpacing;
                }

                if (showShortcuts) {
                        // 排序模式
                        if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18) {
                                List<Component> tooltip = new ArrayList<>();
                                tooltip.add(Component.translatable("gui.portablestorage.button.sort_mode"));
                                tooltip.add(Component.literal(" "));
                                String modeKey = switch (warehouse.getSortMode()) {
                                        case 0 -> "gui.portablestorage.sort.count";
                                        case 1 -> "gui.portablestorage.sort.name";
                                        case 2 -> "gui.portablestorage.sort.id";
                                        case 3 -> "gui.portablestorage.sort.time";
                                        default -> "gui.portablestorage.sort.id";
                                };
                                tooltip.add(Component.translatable("gui.portablestorage.current",
                                                Component.translatable(modeKey).withStyle(ChatFormatting.WHITE))
                                                .withStyle(ChatFormatting.YELLOW));
                                List<ClientTooltipComponent> components = tooltip.stream()
                                                .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                                .toList();
                                graphics.renderTooltip(font, components, mouseX, mouseY,
                                                DefaultTooltipPositioner.INSTANCE, null);
                                return;
                        }

                        // 排序顺序
                        int ox = horizontal ? bx + iconSpacing : bx;
                        int oy = horizontal ? by : by + iconSpacing;
                        if (mouseX >= ox && mouseX < ox + 18 && mouseY >= oy && mouseY < oy + 18) {
                                List<Component> tooltip = new ArrayList<>();
                                tooltip.add(Component.translatable("gui.portablestorage.button.sort_order"));
                                tooltip.add(Component.literal(" "));
                                String orderKey = warehouse.isAscending() ? "gui.portablestorage.order.ascending"
                                                : "gui.portablestorage.order.descending";
                                tooltip.add(Component.translatable("gui.portablestorage.current",
                                                Component.translatable(orderKey).withStyle(ChatFormatting.WHITE))
                                                .withStyle(ChatFormatting.YELLOW));
                                List<ClientTooltipComponent> components = tooltip.stream()
                                                .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                                .toList();
                                graphics.renderTooltip(font, components, mouseX, mouseY,
                                                DefaultTooltipPositioner.INSTANCE, null);
                                return;
                        }

                        // 快速交互
                        int qx = horizontal ? bx + iconSpacing * 2 : bx;
                        int qy = horizontal ? by : by + iconSpacing * 2;
                        if (mouseX >= qx && mouseX < qx + 18 && mouseY >= qy && mouseY < qy + 18) {
                                List<Component> tooltip = new ArrayList<>();
                                tooltip.add(Component.translatable("gui.portablestorage.button.quick_interaction"));
                                tooltip.add(Component.literal(" "));
                                boolean on = warehouse.isQuickInteraction();
                                tooltip.add(Component.translatable("gui.portablestorage.current",
                                                Component.translatable(on ? "gui.portablestorage.on"
                                                                : "gui.portablestorage.off")
                                                                .withStyle(on ? ChatFormatting.GREEN
                                                                                : ChatFormatting.RED))
                                                .withStyle(ChatFormatting.YELLOW));
                                List<ClientTooltipComponent> components = tooltip.stream()
                                                .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                                .toList();
                                graphics.renderTooltip(font, components, mouseX, mouseY,
                                                DefaultTooltipPositioner.INSTANCE, null);
                                return;
                        }

                        // 智能折叠
                        int sx = horizontal ? bx + iconSpacing * 3 : bx;
                        int sy = horizontal ? by : by + iconSpacing * 3;
                        if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                                List<Component> tooltip = new ArrayList<>();
                                tooltip.add(Component.translatable("gui.portablestorage.button.smart_collapse"));
                                tooltip.add(Component.literal(" "));
                                boolean on = warehouse.isSmartCollapse();
                                tooltip.add(Component.translatable("gui.portablestorage.current",
                                                Component.translatable(on ? "gui.portablestorage.on"
                                                                : "gui.portablestorage.off")
                                                                .withStyle(on ? ChatFormatting.GREEN
                                                                                : ChatFormatting.RED))
                                                .withStyle(ChatFormatting.YELLOW));
                                List<ClientTooltipComponent> components = tooltip.stream()
                                                .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                                .toList();
                                graphics.renderTooltip(font, components, mouseX, mouseY,
                                                DefaultTooltipPositioner.INSTANCE, null);
                                return;
                        }

                        // 合成补充
                        int rx = horizontal ? bx + iconSpacing * 4 : bx;
                        int ry = horizontal ? by : by + iconSpacing * 4;
                        if (mouseX >= rx && mouseX < rx + 18 && mouseY >= ry && mouseY < ry + 18) {
                                List<Component> tooltip = new ArrayList<>();
                                tooltip.add(Component.translatable("gui.portablestorage.button.craft_refill"));
                                tooltip.add(Component.literal(" "));
                                boolean on = warehouse.isCraftRefill();
                                tooltip.add(Component.translatable("gui.portablestorage.current",
                                                Component.translatable(on ? "gui.portablestorage.on"
                                                                : "gui.portablestorage.off")
                                                                .withStyle(on ? ChatFormatting.GREEN
                                                                                : ChatFormatting.RED))
                                                .withStyle(ChatFormatting.YELLOW));
                                List<ClientTooltipComponent> components = tooltip.stream()
                                                .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                                .toList();
                                graphics.renderTooltip(font, components, mouseX, mouseY,
                                                DefaultTooltipPositioner.INSTANCE, null);
                                return;
                        }
                }

                // 合成台图标
                if (!warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
                        int craftingX = horizontal ? (bx + (showShortcuts ? iconSpacing * 5 : 0)) : bx;
                        int craftingY = horizontal ? by : (by + (showShortcuts ? iconSpacing * 5 : 0));
                        if (mouseX >= craftingX && mouseX < craftingX + 18 && mouseY >= craftingY
                                        && mouseY < craftingY + 18) {
                                boolean isCrafting = net.minecraft.client.Minecraft
                                                .getInstance().screen instanceof com.portablestorage.screen.CraftingWarehouseScreen;
                                List<Component> tooltip = new ArrayList<>();
                                tooltip.add(Component.translatable(
                                                isCrafting ? "gui.portablestorage.button.back"
                                                                : "gui.portablestorage.button.open_crafting"));
                                List<ClientTooltipComponent> components = tooltip.stream()
                                                .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                                .toList();
                                graphics.renderTooltip(font, components, mouseX, mouseY,
                                                DefaultTooltipPositioner.INSTANCE, null);
                                return;
                        }
                }
        }

        public static void renderFoldTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY,
                        PlayerWarehouse warehouse) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(
                                warehouse.isFolded() ? "gui.portablestorage.button.unfold"
                                                : "gui.portablestorage.button.fold"));
                tooltip.add(Component.literal(" "));
                tooltip.add(
                                Component.translatable("gui.portablestorage.button.settings_hint")
                                                .withStyle(ChatFormatting.DARK_GRAY));
                List<ClientTooltipComponent> components = tooltip.stream()
                                .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                .toList();
                graphics.renderTooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }

        public static void renderAllTooltips(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX,
                        int mouseY, PlayerWarehouse warehouse, int imageHeight, int foldX, int foldY,
                        boolean indentSidebar) {
                // 检查折叠按钮 tooltip
                if (mouseX >= foldX && mouseX < foldX + 18 && mouseY >= foldY && mouseY < foldY + 18) {
                        renderFoldTooltip(graphics, font, mouseX, mouseY, warehouse);
                        return;
                }

                if (warehouse.isFolded())
                        return;

                // 渲染共享状态提示
                renderStatusTooltip(graphics, font, leftPos, topPos, mouseX, mouseY, warehouse, imageHeight);

                // 渲染升级槽位提示
                renderUpgradeTooltips(graphics, font, leftPos, topPos, mouseX, mouseY, warehouse, imageHeight);

                renderSidebarTooltips(graphics, font, leftPos, topPos, mouseX, mouseY, warehouse, imageHeight,
                                indentSidebar);
        }

        public static void renderUpgradeTooltips(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX,
                        int mouseY, PlayerWarehouse warehouse, int imageHeight) {
                int x = leftPos + WarehouseConstants.getWarehouseXOffset();
                int y = topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight);
                int upgradeSlotX = x + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X;
                int upgradeSlotY = y + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y;
                int rows = warehouse.getVisibleRows();

                List<com.portablestorage.upgrade.UpgradeType> allUpgrades = com.portablestorage.upgrade.UpgradeRegistry
                                .getAllUpgrades();
                int upgradeOffset = warehouse.getUpgradeScrollOffset();

                for (int i = 0; i < rows; i++) {
                        int slotY = upgradeSlotY + i * WarehouseConstants.SLOT_SIZE;
                        if (mouseX >= upgradeSlotX && mouseX < upgradeSlotX + 16 && mouseY >= slotY
                                        && mouseY < slotY + 16) {
                                int upgradeIndex = i + upgradeOffset;
                                if (upgradeIndex < allUpgrades.size()) {
                                        com.portablestorage.upgrade.UpgradeType type = allUpgrades.get(upgradeIndex);
                                        ItemStack stack = warehouse.getUpgrade(type.getId());

                                        List<Component> finalTooltip = new ArrayList<>();
                                        if (!stack.isEmpty()) {
                                                // 如果有物品，先获取物品的原版提示
                                                finalTooltip.addAll(net.minecraft.client.gui.screens.Screen
                                                                .getTooltipFromItem(net.minecraft.client.Minecraft
                                                                                .getInstance(), stack));
                                                finalTooltip.add(Component.literal(" ")); // 分隔符
                                        }

                                        // 添加升级自定义提示
                                        finalTooltip.addAll(type.getTooltip(warehouse, stack));

                                        if (!finalTooltip.isEmpty()) {
                                                List<ClientTooltipComponent> components = finalTooltip.stream()
                                                                .map(c -> ClientTooltipComponent
                                                                                .create(c.getVisualOrderText()))
                                                                .toList();
                                                graphics.renderTooltip(font, components, mouseX, mouseY,
                                                                DefaultTooltipPositioner.INSTANCE, null);
                                        }
                                }
                                break;
                        }
                }
        }

        public static void renderScrollbar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                        PlayerWarehouse warehouse) {
                int rows = warehouse.getVisibleRows();
                int scrollbarX = x + WarehouseConstants.getScrollbarXOffset();
                int scrollbarY = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
                int scrollbarHeight = rows * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;

                if (scrollbarHeight > 0) {
                        graphics.fill(scrollbarX, scrollbarY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH,
                                        scrollbarY + scrollbarHeight, WarehouseConstants.SCROLLBAR_BG_COLOR);
                        int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / 9.0);
                        int thumbHeight = (totalRows <= rows) ? scrollbarHeight
                                        : Math.max(10, (int) (scrollbarHeight * ((float) rows / totalRows)));
                        int maxOffset = Math.max(0, totalRows - rows);
                        int thumbY = scrollbarY + (maxOffset == 0 ? 0
                                        : (warehouse.getScrollOffset() * (scrollbarHeight - thumbHeight) / maxOffset));

                        boolean hovered = mouseX >= scrollbarX
                                        && mouseX <= scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH
                                        && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
                        int thumbColor = hovered ? WarehouseConstants.SCROLLBAR_THUMB_HOVER_COLOR
                                        : WarehouseConstants.SCROLLBAR_THUMB_COLOR;

                        graphics.fill(scrollbarX, thumbY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH,
                                        thumbY + thumbHeight,
                                        thumbColor);
                        graphics.fill(scrollbarX - 1, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH,
                                        thumbY,
                                        WarehouseConstants.SCROLLBAR_BORDER_LIGHT);
                        graphics.fill(scrollbarX - 1, thumbY, scrollbarX, thumbY + thumbHeight,
                                        WarehouseConstants.SCROLLBAR_BORDER_LIGHT);
                        graphics.fill(scrollbarX, thumbY + thumbHeight,
                                        scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1,
                                        thumbY + thumbHeight + 1, WarehouseConstants.SCROLLBAR_BORDER_DARK);
                        graphics.fill(scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY - 1,
                                        scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight,
                                        WarehouseConstants.SCROLLBAR_BORDER_DARK);
                }
        }

        public static void renderQuantityTexts(GuiGraphics graphics, Font font, int leftPos, int topPos,
                        PlayerWarehouse warehouse, int imageHeight) {
                if (warehouse.isFolded())
                        return;
                int startX = leftPos + WarehouseConstants.getSlotLogicX();
                int startY = topPos + WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows(), imageHeight);

                for (int i = 0; i < warehouse.getVisibleRows() * 9; i++) {
                        long count = warehouse.getRealCount(i);
                        // 静态锁定模式下，槽位有物品但数量为 0 时显示灰色 0
                        boolean shouldShowZero = count == 0 && warehouse.isFrozen() && !warehouse.getItem(i).isEmpty();

                        if (count > 1 || shouldShowZero) {
                                String countStr = WarehouseUtils.formatCount(count);
                                int row = i / 9;
                                int col = i % 9;
                                float scale = WarehouseConstants.QUANTITY_TEXT_SCALE;
                                int textX = startX + col * WarehouseConstants.SLOT_SIZE
                                                + WarehouseConstants.QUANTITY_TEXT_X_RELATIVE
                                                - Math.round(font.width(countStr) * scale);
                                int textY = startY + row * WarehouseConstants.SLOT_SIZE
                                                + WarehouseConstants.QUANTITY_TEXT_Y_RELATIVE;

                                int color = shouldShowZero ? WarehouseConstants.QUANTITY_TEXT_COLOR_ZERO
                                                : WarehouseConstants.QUANTITY_TEXT_COLOR;
                                graphics.pose().pushMatrix();
                                graphics.pose().translate(textX, textY);
                                graphics.pose().scale(scale, scale);
                                graphics.drawString(font, countStr, 0, 0, color, true);
                                graphics.pose().popMatrix();
                        }
                }
        }

        public static void drawNinePatch(GuiGraphics graphics, Identifier texture, int x, int y, int width,
                        int height, int cornerSize) {
                int textureSize = WarehouseConstants.GUI_TEXTURE_SIZE;
                int targetCenterWidth = width - cornerSize * 2;
                int targetCenterHeight = height - cornerSize * 2;

                // 四个角
                blitRegion(graphics, texture, x, y, 0, 0, cornerSize, cornerSize, cornerSize, cornerSize, textureSize,
                                textureSize);
                blitRegion(graphics, texture, x + width - cornerSize, y, textureSize - cornerSize, 0,
                                cornerSize, cornerSize, cornerSize, cornerSize, textureSize, textureSize);
                blitRegion(graphics, texture, x, y + height - cornerSize, 0, textureSize - cornerSize,
                                cornerSize, cornerSize, cornerSize, cornerSize, textureSize, textureSize);
                blitRegion(graphics, texture, x + width - cornerSize, y + height - cornerSize,
                                textureSize - cornerSize, textureSize - cornerSize,
                                cornerSize, cornerSize, cornerSize, cornerSize, textureSize, textureSize);

                // 上下边
                blitRegion(graphics, texture, x + cornerSize, y, cornerSize, 0,
                                targetCenterWidth, cornerSize, textureSize - cornerSize * 2, cornerSize, textureSize,
                                textureSize);
                blitRegion(graphics, texture, x + cornerSize, y + height - cornerSize, cornerSize,
                                textureSize - cornerSize, targetCenterWidth, cornerSize,
                                textureSize - cornerSize * 2, cornerSize, textureSize, textureSize);

                // 左右边
                blitRegion(graphics, texture, x, y + cornerSize, 0, cornerSize,
                                cornerSize, targetCenterHeight, cornerSize, textureSize - cornerSize * 2, textureSize,
                                textureSize);
                blitRegion(graphics, texture, x + width - cornerSize, y + cornerSize,
                                textureSize - cornerSize, cornerSize,
                                cornerSize, targetCenterHeight, cornerSize, textureSize - cornerSize * 2, textureSize,
                                textureSize);

                // 中心区域
                blitRegion(graphics, texture, x + cornerSize, y + cornerSize,
                                cornerSize, cornerSize, targetCenterWidth, targetCenterHeight,
                                textureSize - cornerSize * 2, textureSize - cornerSize * 2, textureSize,
                                textureSize);
        }

        public static void renderIconButton(GuiGraphics graphics, int x, int y, int iconIndex, int mouseX, int mouseY) {
                int u = (iconIndex % 5) * WarehouseConstants.ICON_SIZE;
                int v = (iconIndex / 5) * WarehouseConstants.ICON_SIZE;
                blitRegion(graphics, WAREHOUSE_ICON_TEXTURE, x + 1, y + 1, u, v, WarehouseConstants.ICON_SIZE,
                                WarehouseConstants.ICON_SIZE, WarehouseConstants.ICON_SIZE,
                                WarehouseConstants.ICON_SIZE, WarehouseConstants.ICON_TEXTURE_WIDTH,
                                WarehouseConstants.ICON_TEXTURE_HEIGHT);
        }

        public static void renderPlusMinusButtons(GuiGraphics graphics, Font font, int x, int y, int mouseX,
                        int mouseY) {
                renderTinyButton(graphics, font, x, y, "-", mouseX, mouseY);
                renderTinyButton(graphics, font,
                                x + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING, y,
                                "+", mouseX,
                                mouseY);
        }

        public static void renderTinyButton(GuiGraphics graphics, Font font, int x, int y, String text, int mouseX,
                        int mouseY) {
                boolean hovered = mouseX >= x && mouseX < x + WarehouseConstants.TINY_BUTTON_SIZE && mouseY >= y
                                && mouseY < y + WarehouseConstants.TINY_BUTTON_SIZE;
                int color = hovered ? WarehouseConstants.BUTTON_HOVER_COLOR : WarehouseConstants.BUTTON_COLOR;
                graphics.fill(x, y, x + WarehouseConstants.TINY_BUTTON_SIZE, y + WarehouseConstants.TINY_BUTTON_SIZE,
                                color);
                graphics.fill(x - 1, y - 1, x + WarehouseConstants.TINY_BUTTON_SIZE, y,
                                WarehouseConstants.BUTTON_BORDER_LIGHT);
                graphics.fill(x - 1, y, x, y + WarehouseConstants.TINY_BUTTON_SIZE,
                                WarehouseConstants.BUTTON_BORDER_LIGHT);
                graphics.fill(x, y + WarehouseConstants.TINY_BUTTON_SIZE, x + WarehouseConstants.TINY_BUTTON_SIZE + 1,
                                y + WarehouseConstants.TINY_BUTTON_SIZE + 1, WarehouseConstants.BUTTON_BORDER_DARK);
                graphics.fill(x + WarehouseConstants.TINY_BUTTON_SIZE, y - 1,
                                x + WarehouseConstants.TINY_BUTTON_SIZE + 1,
                                y + WarehouseConstants.TINY_BUTTON_SIZE, WarehouseConstants.BUTTON_BORDER_DARK);
                int textX = x + (WarehouseConstants.TINY_BUTTON_SIZE / 2) - font.width(text) / 2 + 1;
                int textY = y + 2;
                graphics.drawString(font, text, textX, textY, 0xFFFFFFFF, false);
        }

        public static void renderPinnedOverlays(GuiGraphics graphics, int leftPos, int topPos,
                        PlayerWarehouse warehouse,
                        int imageHeight) {
                if (warehouse.isFolded())
                        return;
                int startX = leftPos + WarehouseConstants.getSlotLogicX();
                int startY = topPos + WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows(), imageHeight);

                List<com.portablestorage.component.WarehouseEntry> sorted = warehouse.getSortedEntries();
                int visibleSlots = warehouse.getVisibleRows() * 9;
                int scrollOffset = warehouse.getScrollOffset() * 9;

                for (int i = 0; i < visibleSlots; i++) {
                        int actualIndex = i + scrollOffset;
                        if (actualIndex >= 0 && actualIndex < sorted.size()) {
                                if (sorted.get(actualIndex).isPinned()) {
                                        int row = i / 9;
                                        int col = i % 9;
                                        int x = startX + col * WarehouseConstants.SLOT_SIZE;
                                        int y = startY + row * WarehouseConstants.SLOT_SIZE;
                                        // 渲染半透明黄色覆盖层，覆盖物品渲染区域
                                        graphics.fill(x, y, x + 16, y + 16, WarehouseConstants.MASK_YELLOW);
                                }
                        }
                }
        }

        private static void renderSharingStatus(GuiGraphics graphics, int x, int y, PlayerWarehouse warehouse) {
                int baseStatusX = x + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X + 7; // 居中于 18px 宽度的升级列
                int statusY = y + 12; // 位于第一个升级槽位 (21) 上方

                int pointColor;
                int borderColor;

                boolean hasBarrel = !warehouse.getUpgrade(com.portablestorage.upgrade.BarrelUpgrade.ID).isEmpty();
                boolean isFull = warehouse
                                .getEffectiveType() == com.portablestorage.component.PlayerWarehouse.WarehouseType.FULL;
                List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
                boolean isShared = group.size() > 1;

                int statusX = baseStatusX;
                boolean hasConflict = warehouse.isSharingConflict();

                if ((hasBarrel && !isFull) || hasConflict) {
                        pointColor = WarehouseConstants.STATUS_RED; // 红色：有问题
                        borderColor = WarehouseConstants.STATUS_RED_BORDER;
                } else if (isShared) {
                        pointColor = WarehouseConstants.STATUS_GREEN; // 绿色：共享中
                        borderColor = WarehouseConstants.STATUS_GREEN_BORDER;
                        statusX -= 6; // 共享时向左移动 6px
                } else {
                        pointColor = WarehouseConstants.STATUS_GRAY; // 灰色：未共享
                        borderColor = WarehouseConstants.STATUS_GRAY_BORDER;
                }

                // 绘制 1px 外描边
                graphics.fill(statusX - 1, statusY - 1, statusX + 3, statusY, borderColor);
                graphics.fill(statusX - 1, statusY + 2, statusX + 3, statusY + 3, borderColor);
                graphics.fill(statusX - 1, statusY, statusX, statusY + 2, borderColor);
                graphics.fill(statusX + 2, statusY, statusX + 3, statusY + 2, borderColor);

                // 绘制 2x2 状态点
                graphics.fill(statusX, statusY, statusX + 2, statusY + 2, pointColor);

                // 渲染共享组玩家头像（最多 3 个，向右叠加）
                if (isShared) {
                        int avatarX = statusX + 5;
                        int count = 0;
                        UUID localPlayerUuid = Minecraft.getInstance().player != null
                                        ? Minecraft.getInstance().player.getUUID()
                                        : null;

                        for (PlayerWarehouse pw : group) {
                                if (pw.getOwnerUuid().equals(localPlayerUuid))
                                        continue; // 跳过本地玩家

                                // 头像尺寸 8px，重叠 4px 实现叠加效果
                                renderPlayerFace(graphics, pw.getOwnerUuid(), avatarX + count * 4, statusY - 3, 8);
                                count++;
                                if (count >= 3)
                                        break;
                        }
                }
        }

        private static void renderPlayerFace(GuiGraphics graphics, UUID uuid, int x, int y, int size) {
                // 绘制 1px 灰色描边
                graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, WarehouseConstants.AVATAR_BORDER);

                // 当前使用默认 Steve 皮肤，避免依赖复杂的异步皮肤加载 API
                Identifier texture = DEFAULT_STEVE_TEXTURE;

                // 渲染脸部纹理（基础层）
                blitRegion(graphics, texture, x, y, 8, 8, size, size, 8, 8, 64, 64);
                // 渲染帽子/覆盖层
                blitRegion(graphics, texture, x, y, 40, 8, size, size, 8, 8, 64, 64);
        }

        private static void blitRegion(GuiGraphics graphics, Identifier texture, int x, int y, int u, int v,
                        int width, int height, int regionWidth, int regionHeight, int textureWidth,
                        int textureHeight) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth,
                                regionHeight, textureWidth, textureHeight);
        }

        public static boolean isOverSharingStatus(double mouseX, double mouseY, int leftPos, int topPos,
                        PlayerWarehouse warehouse, int imageHeight) {
                if (WarehouseConstants.getUpgradeColumnWidth() <= 0)
                        return false;

                int x = leftPos + WarehouseConstants.getWarehouseXOffset();
                int y = topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight);
                int baseStatusX = x + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X + 7;
                int statusY = y + 12;

                List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
                boolean isShared = group.size() > 1;

                int statusX = isShared ? baseStatusX - 6 : baseStatusX;
                // 共享状态下判定区域需覆盖头像区域（4px 状态点 + 24px 头像）
                int hitWidth = isShared ? 28 : 4;

                return mouseX >= statusX - 1 && mouseX < statusX + hitWidth && mouseY >= statusY - 1
                                && mouseY < statusY + 3;
        }

        public static void renderStatusTooltip(GuiGraphics graphics, Font font, int leftPos, int topPos, int mouseX,
                        int mouseY, PlayerWarehouse warehouse, int imageHeight) {
                if (isOverSharingStatus(mouseX, mouseY, leftPos, topPos, warehouse, imageHeight)) {
                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(Component.translatable("gui.portablestorage.status.title"));
                        tooltip.add(Component.literal(" "));

                        boolean hasBarrel = !warehouse.getUpgrade(com.portablestorage.upgrade.BarrelUpgrade.ID)
                                        .isEmpty();
                        boolean isFull = warehouse
                                        .getEffectiveType() == com.portablestorage.component.PlayerWarehouse.WarehouseType.FULL;
                        List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
                        boolean isShared = group.size() > 1;
                        boolean hasConflict = warehouse.isSharingConflict();

                        if (hasBarrel && !isFull) {
                                tooltip.add(Component.translatable("gui.portablestorage.status.problem"));
                        } else if (hasConflict) {
                                tooltip.add(Component.translatable("gui.portablestorage.status.conflict"));
                                tooltip.add(Component.translatable("gui.portablestorage.status.conflict.desc"));
                        } else if (isShared) {
                                tooltip.add(Component.translatable("gui.portablestorage.status.shared"));

                                // 显示共享组内玩家列表（仅使用本地记录的名字）
                                tooltip.add(Component.literal(" "));
                                tooltip.add(Component
                                                .translatable("gui.portablestorage.status.shared_with",
                                                                group.size() - 1)
                                                .withStyle(ChatFormatting.GRAY));

                                int count = 0;
                                UUID localPlayerUuid = net.minecraft.client.Minecraft.getInstance().player != null
                                                ? net.minecraft.client.Minecraft.getInstance().player.getUUID()
                                                : null;
                                for (PlayerWarehouse pw : group) {
                                        UUID uuid = pw.getOwnerUuid();
                                        if (uuid.equals(localPlayerUuid))
                                                continue;

                                        if (count >= 5) {
                                                tooltip.add(Component.literal("  ...")
                                                                .withStyle(ChatFormatting.DARK_GRAY));
                                                break;
                                        }

                                        String name = pw.getOwnerName();
                                        tooltip.add(Component.literal("  - ").withStyle(ChatFormatting.DARK_GRAY)
                                                        .append(Component.literal(name)
                                                                        .withStyle(ChatFormatting.GRAY)));
                                        count++;
                                }
                        } else {
                                tooltip.add(Component.translatable("gui.portablestorage.status.not_shared"));
                        }
                        List<ClientTooltipComponent> components = tooltip.stream()
                                        .map(c -> ClientTooltipComponent.create(c.getVisualOrderText()))
                                        .toList();
                        graphics.renderTooltip(font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE,
                                        null);
                }
        }
}
