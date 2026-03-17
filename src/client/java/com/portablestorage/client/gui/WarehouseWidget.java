package com.portablestorage.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.config.YACLConfig;
import com.portablestorage.mixin.client.AbstractContainerScreenAccessor;
import com.portablestorage.mixin.client.ScreenAccessor;
import com.portablestorage.network.C2SDoubleClickQuickStorePayload;
import com.portablestorage.network.C2SDropWarehouseItemPayload;
import com.portablestorage.network.C2STogglePinnedPayload;
import com.portablestorage.network.C2SUpdateFrozenStatePayload;
import com.portablestorage.network.C2SUpdateWarehouseStatePayload;
import com.portablestorage.network.C2SUpgradeInteractionPayload;
import com.portablestorage.network.OpenCraftingPayload;
import com.portablestorage.network.QuickTransferPayload;
import com.portablestorage.network.RefillPayload;
import com.portablestorage.util.StoragePosition;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseRenderer;
import com.portablestorage.util.WarehouseSetting;
import com.portablestorage.util.WarehouseUtils;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 仓库界面组件
 * 管理仓库 UI 的渲染、交互和状态更新
 */
public class WarehouseWidget {
    private static final Identifier WAREHOUSE_SLOT_TEXTURE = com.portablestorage.PortableStorage
            .id("textures/gui/slot.png");

    private final AbstractContainerScreen<?> screen;
    private final PlayerWarehouse warehouse;

    private EditBox searchBox;
    private boolean isDraggingScrollbar = false;
    private boolean isDraggingUpgradeScrollbar = false;
    private long lastSearchUpdateTime = 0;
    private String pendingSearchText = null;

    // 双击检测
    private long lastClickTime = 0;
    private double lastClickX = -1;
    private double lastClickY = -1;
    private static final long DOUBLE_CLICK_TIME_MS = 300; // 双击时间窗口（毫秒）
    private static final double DOUBLE_CLICK_DISTANCE = 5.0; // 双击允许的最大距离

    // 屏幕刷新状态追踪
    private boolean lastWorkbenchStatus = false;
    private boolean lastEnabledStatus = false;

    // 合成补充状态
    private ItemStack lastCraftingOutput = ItemStack.EMPTY;
    private final Map<Integer, ItemStack> lastCraftingStacks = new HashMap<>();
    private long lastCraftRefillCheck = 0;

    public WarehouseWidget(AbstractContainerScreen<?> screen) {
        this.screen = screen;
        this.warehouse = ModComponents.get(Minecraft.getInstance().player)
                .getWarehouse(Minecraft.getInstance().player.getUUID());
    }

    public boolean shouldShow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
            return false;

        // 排除创造模式标准背包界面
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            return false;
        }

        // 容器界面需要工作台升级才能显示
        if (isContainerInterface() && warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
            return false;
        }

        return warehouse.isEnabled();
    }

    public void refreshPosition() {
        if (!shouldShow())
            return;
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        adjustScreenPosition(rows);
    }

    public void init() {
        if (!shouldShow())
            return;

        var player = Minecraft.getInstance().player;
        if (player == null)
            return;

        this.lastWorkbenchStatus = WarehouseUtils.is3x3Enabled(player);
        this.lastEnabledStatus = warehouse.isEnabled();

        if (warehouse.isEnabled()) {
            int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
            updateSlotPositions();
            adjustScreenPosition(rows);
            initSearchBox();
        }

        if (this.lastWorkbenchStatus && screen instanceof InventoryScreen) {
            initCraftingPositions();
        }
    }

    private void initCraftingPositions() {
        int[] craftIndices = { 1, 2, 46, 3, 4, 47, 48, 49, 50 };
        for (int i = 0; i < craftIndices.length; i++) {
            var slot = screen.getMenu().slots.get(craftIndices[i]);
            ((com.portablestorage.mixin.accessor.SlotAccessor) slot)
                    .setX(WarehouseConstants.CRAFT_3X3_X + (i % 3) * 18);
            ((com.portablestorage.mixin.accessor.SlotAccessor) slot)
                    .setY(WarehouseConstants.CRAFT_3X3_Y + (i / 3) * 18);
        }

        var resultSlot = screen.getMenu().slots.get(0);
        ((com.portablestorage.mixin.accessor.SlotAccessor) resultSlot).setX(WarehouseConstants.CRAFT_RESULT_X);
        ((com.portablestorage.mixin.accessor.SlotAccessor) resultSlot).setY(WarehouseConstants.CRAFT_RESULT_Y);
    }

    private void updateSlotPositions() {
        int imageHeight = ((AbstractContainerScreenAccessor) screen).portablestorage$getImageHeight();
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof com.portablestorage.upgrade.UpgradeSlot upgradeSlot) {
                int index = upgradeSlot.getVisualIndex();
                int upgradeX = WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X;
                int upgradeYBase = WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight,
                        warehouse.isFolded()) + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y;
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot).setX(upgradeX);
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot)
                        .setY(upgradeYBase + index * WarehouseConstants.SLOT_SIZE);
            } else if (slot.container instanceof PlayerWarehouse) {
                int index = slot.getContainerSlot();
                int row = index / WarehouseConstants.SLOTS_PER_ROW;
                int col = index % WarehouseConstants.SLOTS_PER_ROW;
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot)
                        .setX(WarehouseConstants.getSlotLogicX() + col * WarehouseConstants.SLOT_SIZE);
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot)
                        .setY(WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows(), imageHeight,
                                warehouse.isFolded()) + row * WarehouseConstants.SLOT_SIZE);
            }
        }
    }

    public void adjustScreenPosition(int rows) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int screenWidth = screen.width;
        int screenHeight = screen.height;
        int imageWidth = screenAccessor.portablestorage$getImageWidth();
        int imageHeight = screenAccessor.portablestorage$getImageHeight();

        // 重置为标准居中位置，防止多次调用导致的漂移
        int defaultLeftPos = (screenWidth - imageWidth) / 2;
        int defaultTopPos = (screenHeight - imageHeight) / 2;

        int xOffset = 0;
        int yOffset = 0;

        if (ModConfig.offsetInventory && !warehouse.isFolded() && (screen instanceof InventoryScreen
                || screen instanceof com.portablestorage.screen.CraftingWarehouseScreen
                || screen instanceof com.portablestorage.screen.BoundBarrelScreen)) {
            StoragePosition pos = ModConfig.storagePosition;
            boolean folded = warehouse.isFolded();
            int warehouseWidth = WarehouseConstants.getWarehouseWidth();
            int warehouseHeight = WarehouseConstants.getWarehouseHeight(rows, folded);
            int spacingX = WarehouseConstants.WAREHOUSE_X_SPACING;
            int spacingY = WarehouseConstants.WAREHOUSE_Y_SPACING;

            if (pos.isVertical()) {
                // 垂直模式：(仓库高度 + 间距) / 2
                yOffset = (warehouseHeight + spacingY) / 2;
                if (pos == StoragePosition.BOTTOM)
                    yOffset = -yOffset;
            } else {
                // 水平模式：(仓库宽度 + 间距) / 2
                xOffset = (warehouseWidth + spacingX) / 2;
                if (pos == StoragePosition.RIGHT)
                    xOffset = -xOffset;
            }
        }

        int targetLeftPos = defaultLeftPos + xOffset;
        int targetTopPos = defaultTopPos + yOffset;

        int dx = targetLeftPos - screenAccessor.portablestorage$getLeftPos();
        int dy = targetTopPos - screenAccessor.portablestorage$getTopPos();

        screenAccessor.portablestorage$setLeftPos(targetLeftPos);
        screenAccessor.portablestorage$setTopPos(targetTopPos);

        // 仅在生存模式背包且启用 3x3 时调整 imageHeight，避免破坏容器界面对齐
        if (screen instanceof InventoryScreen && WarehouseUtils.is3x3Enabled(Minecraft.getInstance().player)) {
            screenAccessor.portablestorage$setImageHeight(WarehouseConstants.VANILLA_INVENTORY_HEIGHT);
        }

        // 同步移动所有关联组件（输入框、原版按钮等）
        if (dx != 0 || dy != 0) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    widget.setX(widget.getX() + dx);
                    widget.setY(widget.getY() + dy);
                }
            }
        }
    }

    private void initSearchBox() {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        int sbX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset()
                + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbY = screenAccessor.portablestorage$getTopPos()
                + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight, warehouse.isFolded())
                + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbW = WarehouseConstants.SEARCH_BOX_WIDTH - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        int sbH = WarehouseConstants.SEARCH_BOX_HEIGHT - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;

        this.searchBox = new EditBox(((ScreenAccessor) screen).portablestorage$getFont(), sbX, sbY, sbW, sbH,
                Component.literal(""));
        this.searchBox.setResponder(text -> {
            this.pendingSearchText = text;
            this.lastSearchUpdateTime = System.currentTimeMillis();
        });
        this.searchBox.setEditable(true);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setTextColorUneditable(0xFFFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.portablestorage.search")
                .withStyle(style -> style.withColor(0x666666)));
        this.searchBox.visible = !warehouse.isFolded();
        this.searchBox.active = !warehouse.isFolded();

        ((ScreenAccessor) screen).invokeAddRenderableWidget(this.searchBox);
    }

    /**
     * 渲染仓库背景（在 renderBg 之后调用，在原版槽位高亮之前）
     */
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        if (WarehouseUtils.is3x3Enabled(Minecraft.getInstance().player) && screen instanceof InventoryScreen) {
            renderCraftingBg(graphics);
        }

        if (!shouldShow())
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
            return;

        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        int leftPos = screenAccessor.portablestorage$getLeftPos();
        int topPos = screenAccessor.portablestorage$getTopPos();
        int x = leftPos + WarehouseConstants.getWarehouseXOffset();
        int y = topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight,
                warehouse.isFolded());

        WarehouseRenderer.renderBackground(graphics, x, y, mouseX, mouseY, warehouse,
                ((ScreenAccessor) screen).portablestorage$getFont());

        // 计算折叠按钮位置
        int foldX, foldY;
        boolean indentSidebar = false;
        if (screen instanceof com.portablestorage.screen.CraftingWarehouseScreen) {
            foldX = leftPos + 84;
            foldY = topPos + 53;
        } else if (isContainerInterface()) {
            if (warehouse.isFolded()) {
                foldX = leftPos + 172; // 背包图片右边缘附近
                foldY = topPos + imageHeight - 24; // 快捷栏高度
            } else {
                foldX = x + WarehouseConstants.getSidebarXOffset();
                foldY = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows(), imageHeight,
                        warehouse.isFolded());
                indentSidebar = true;
            }
        } else if (screen instanceof com.portablestorage.screen.BoundBarrelScreen) {
            foldX = leftPos + 151;
            foldY = topPos + 32;
        } else {
            foldX = leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
            foldY = topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        }
        WarehouseRenderer.renderSidebarButtons(graphics, foldX, foldY, x + WarehouseConstants.getSidebarXOffset(),
                y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows(), imageHeight, warehouse.isFolded()),
                mouseX, mouseY, warehouse, indentSidebar);
    }

    private boolean isContainerInterface() {
        return !(screen instanceof InventoryScreen)
                && !(screen instanceof com.portablestorage.screen.CraftingWarehouseScreen)
                && !(screen instanceof com.portablestorage.screen.BoundBarrelScreen);
    }

    /**
     * 渲染覆盖层和文本（在 render 返回前调用，在原版槽位高亮之后）
     */
    public void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!shouldShow())
            return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
            return;

        // 逻辑和状态检查
        handleFrozenMode();
        checkRefreshNeeded();
        checkCraftRefill();
        handleSearchDebounce();

        // 更新搜索框位置
        updateSearchBoxState();

        // 渲染覆盖层、提示和数量文本
        renderOverlaysAndText(graphics, mouseX, mouseY);
    }

    public void renderPreTooltipOverlays(GuiGraphics graphics) {
        if (!shouldShow()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int leftPos = screenAccessor.portablestorage$getLeftPos();
        int topPos = screenAccessor.portablestorage$getTopPos();
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        var font = ((ScreenAccessor) screen).portablestorage$getFont();

        WarehouseRenderer.renderPinnedOverlays(graphics, leftPos, topPos, warehouse, imageHeight);
        WarehouseRenderer.renderQuantityTexts(graphics, font, leftPos, topPos, warehouse, imageHeight);
    }

    private void renderCraftingBg(GuiGraphics graphics) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int cx = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.CRAFT_3X3_X - 1;
        int cy = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.CRAFT_3X3_Y - 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, WAREHOUSE_SLOT_TEXTURE, cx + col * 18, cy + row * 18,
                        0, 0, 18, 18, 18, 18, 18, 18);
            }
        }
    }

    private void handleFrozenMode() {
        boolean isPressed = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LSHIFT)
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LCONTROL);
        if (isPressed != warehouse.isFrozen()) {
            warehouse.setFrozen(isPressed);
            ClientPlayNetworking.send(new C2SUpdateFrozenStatePayload(isPressed));
        }
    }

    private void checkRefreshNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (warehouse.isEnabled() != lastEnabledStatus
                || WarehouseUtils.is3x3Enabled(minecraft.player) != lastWorkbenchStatus) {
            refreshScreen();
        }
    }

    private void refreshScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (screen instanceof InventoryScreen) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
        } else {
            // 对于容器方块界面，尝试通过 init 重新排布
            screen.init(minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight());
        }
    }

    private void handleSearchDebounce() {
        if (pendingSearchText != null && System.currentTimeMillis() - lastSearchUpdateTime > 150) {
            warehouse.setSearchText(pendingSearchText);
            ClientPlayNetworking
                    .send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(pendingSearchText),
                            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            pendingSearchText = null;
        }
    }

    private void updateSearchBoxState() {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        if (this.searchBox != null) {
            this.searchBox.setX(screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset()
                    + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.setY(screenAccessor.portablestorage$getTopPos()
                    + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight,
                            warehouse.isFolded())
                    + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.visible = !warehouse.isFolded();
            this.searchBox.active = !warehouse.isFolded();
        }
    }

    private void renderOverlaysAndText(GuiGraphics graphics, int mouseX, int mouseY) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int leftPos = screenAccessor.portablestorage$getLeftPos();
        int topPos = screenAccessor.portablestorage$getTopPos();
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        var font = ((ScreenAccessor) screen).portablestorage$getFont();

        int foldX, foldY;
        boolean indentSidebar = false;
        int x = leftPos + WarehouseConstants.getWarehouseXOffset();
        int y = topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight,
                warehouse.isFolded());
        if (screen instanceof com.portablestorage.screen.CraftingWarehouseScreen) {
            foldX = leftPos + 84;
            foldY = topPos + 53;
        } else if (isContainerInterface()) {
            if (warehouse.isFolded()) {
                foldX = leftPos + 172;
                foldY = topPos + imageHeight - 24;
            } else {
                foldX = x + WarehouseConstants.getSidebarXOffset();
                foldY = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows(), imageHeight,
                        warehouse.isFolded());
                indentSidebar = true;
            }
        } else if (screen instanceof com.portablestorage.screen.BoundBarrelScreen) {
            foldX = leftPos + 151;
            foldY = topPos + 32;
        } else {
            foldX = leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
            foldY = topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        }

        WarehouseRenderer.renderAllTooltips(graphics, font, leftPos, topPos, mouseX, mouseY, warehouse, imageHeight,
                foldX, foldY, indentSidebar);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!shouldShow())
            return false;

        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;

        // 升级槽位交互
        if (!warehouse.isFolded() && (button == 1 || button == 2)) {
            for (Slot slot : screen.getMenu().slots) {
                if (slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
                    int slotX = screenAccessor.portablestorage$getLeftPos() + slot.x;
                    int slotY = screenAccessor.portablestorage$getTopPos() + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16
                            && slot.hasItem()) {
                        List<com.portablestorage.upgrade.UpgradeType> all = com.portablestorage.upgrade.UpgradeRegistry
                                .getAllUpgrades();
                        int visualIndex = ((com.portablestorage.upgrade.UpgradeSlot) slot).getVisualIndex();
                        int actualIndex = visualIndex + warehouse.getUpgradeScrollOffset();
                        if (actualIndex >= 0 && actualIndex < all.size()) {
                            ClientPlayNetworking.send(
                                    new C2SUpgradeInteractionPayload(all.get(actualIndex).getId(), button));
                            return true;
                        }
                    }
                }
            }
        }

        // 仓库物品格中键：切换收藏
        if (!warehouse.isFolded() && button == 2) {
            int baseX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getSlotLogicX();
            int baseY = screenAccessor.portablestorage$getTopPos()
                    + WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows(),
                            screenAccessor.portablestorage$getImageHeight(), warehouse.isFolded());
            int gridW = WarehouseConstants.SLOTS_PER_ROW * WarehouseConstants.SLOT_SIZE;
            int gridH = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
            if (mouseX >= baseX && mouseX < baseX + gridW && mouseY >= baseY && mouseY < baseY + gridH) {
                int col = (int) ((mouseX - baseX) / WarehouseConstants.SLOT_SIZE);
                int row = (int) ((mouseY - baseY) / WarehouseConstants.SLOT_SIZE);
                int visibleIndex = row * WarehouseConstants.SLOTS_PER_ROW + col;
                int sortedIndex = visibleIndex + warehouse.getScrollOffset() * WarehouseConstants.SLOTS_PER_ROW;
                if (sortedIndex >= 0 && sortedIndex < warehouse.getSortedEntries().size()) {
                    ClientPlayNetworking.send(new C2STogglePinnedPayload(visibleIndex));
                    return true;
                }
            }
        }

        // 主槽位交互（左键）
        Slot clickedSlot = getHoveredSlot(mouseX, mouseY);
        if (clickedSlot != null && clickedSlot.container instanceof PlayerWarehouse && clickedSlot.hasItem()) {
            if (button == 0) { // 左键：智能折叠搜索
                ItemStack stack = clickedSlot.getItem();
                var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                boolean isCollapsed = false;
                if (customData != null) {
                    java.util.Optional<Boolean> collapsedOpt = customData.copyTag()
                            .getBoolean(WarehouseConstants.SMART_COLLAPSE_TAG);
                    isCollapsed = collapsedOpt.orElse(false);
                }
                if (warehouse.isSmartCollapse() && warehouse.getSearchText().isEmpty() && isCollapsed) {
                    String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                            .toString();
                    String newSearch = "!" + itemId + "!";
                    if (this.searchBox != null) {
                        this.searchBox.setValue(newSearch);
                        warehouse.setSearchText(newSearch);
                        ClientPlayNetworking
                                .send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(newSearch),
                                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
                    }
                    return true;
                }
            }
        }

        // 快速交互（Shift + 点击）
        // 未适配界面不应响应快速存取
        if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), InputConstants.KEY_LSHIFT)
                && warehouse.isQuickInteraction()
                && !warehouse.isFolded()
                && com.portablestorage.handler.WarehouseMenuHandler.isAdaptedMenu(screen.getMenu())) {
            if (clickedSlot != null && (clickedSlot.container instanceof PlayerWarehouse
                    || clickedSlot.container instanceof net.minecraft.world.entity.player.Inventory)) {
                // 检测双击事件
                long currentTime = System.currentTimeMillis();
                boolean isDoubleClick = false;

                if (button == 0 && currentTime - lastClickTime < DOUBLE_CLICK_TIME_MS) {
                    double distance = Math.sqrt(Math.pow(mouseX - lastClickX, 2) + Math.pow(mouseY - lastClickY, 2));
                    if (distance <= DOUBLE_CLICK_DISTANCE) {
                        isDoubleClick = true;
                    }
                }

                if (isDoubleClick) {
                    // Shift+双击：将背包中所有相同物品存入仓库
                    ItemStack cursorStack = screen.getMenu().getCarried();
                    if (!cursorStack.isEmpty()) {
                        ClientPlayNetworking.send(new C2SDoubleClickQuickStorePayload());
                        lastClickTime = 0; // 重置，避免连续触发
                        lastClickX = -1;
                        lastClickY = -1;
                        return true;
                    }
                } else {
                    // 普通 Shift+左键
                    ClientPlayNetworking.send(new QuickTransferPayload(clickedSlot.index));
                    lastClickTime = currentTime;
                    lastClickX = mouseX;
                    lastClickY = mouseY;
                    return true;
                }
            }
        } else {
            // 重置双击检测（非 Shift 点击）
            lastClickTime = 0;
            lastClickX = -1;
            lastClickY = -1;
        }

        // 折叠按钮
        int foldButtonX, foldButtonY;
        if (screen instanceof com.portablestorage.screen.CraftingWarehouseScreen) {
            foldButtonX = screenAccessor.portablestorage$getLeftPos() + 84;
            foldButtonY = screenAccessor.portablestorage$getTopPos() + 53;
        } else if (isContainerInterface()) {
            if (warehouse.isFolded()) {
                foldButtonX = screenAccessor.portablestorage$getLeftPos() + 172;
                foldButtonY = screenAccessor.portablestorage$getTopPos()
                        + screenAccessor.portablestorage$getImageHeight() - 24;
            } else {
                int whX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
                int whY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(
                        warehouse.getVisibleRows(), screenAccessor.portablestorage$getImageHeight());
                foldButtonX = whX + WarehouseConstants.getSidebarXOffset();
                foldButtonY = whY + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows(),
                        screenAccessor.portablestorage$getImageHeight());
            }
        } else if (screen instanceof com.portablestorage.screen.BoundBarrelScreen) {
            foldButtonX = screenAccessor.portablestorage$getLeftPos() + 151;
            foldButtonY = screenAccessor.portablestorage$getTopPos() + 32;
        } else {
            foldButtonX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
            foldButtonY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        }
        if (mouseX >= foldButtonX && mouseX < foldButtonX + 18 && mouseY >= foldButtonY && mouseY < foldButtonY + 18) {
            if (button == 2) { // 中键：打开设置
                var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
                boolean hasYacl = loader.isModLoaded("yet_another_config_lib_v3");
                if (hasYacl) {
                    minecraft.setScreen(YACLConfig.create(screen));
                } else {
                    if (minecraft.player != null) {
                        minecraft.player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("gui.portablestorage.yacl_missing"),
                                true);
                    }
                }
                return true;
            }
            if (button == 0) { // 左键：折叠/展开
                boolean newFolded = !warehouse.isFolded();
                warehouse.setFolded(newFolded);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(),
                        Optional.of(WarehouseSetting.FOLD.ordinal()), Optional.of(newFolded ? 1 : 0), Optional.empty(),
                        Optional.empty()));
                if (newFolded && this.searchBox != null)
                    this.searchBox.setFocused(false);
                refreshScreen();
                return true;
            }
        }

        // 共享状态
        if (WarehouseRenderer.isOverSharingStatus(mouseX, mouseY, screenAccessor.portablestorage$getLeftPos(),
                screenAccessor.portablestorage$getTopPos(), warehouse,
                screenAccessor.portablestorage$getImageHeight())) {
            if (button == 0) {
                minecraft.setScreen(YACLConfig.createSharingManagementScreen(screen, warehouse));
                return true;
            }
        }

        // 侧边栏和滚动条
        return handleSidebarAndScrollbars(mouseX, mouseY, button);
    }

    private boolean handleSidebarAndScrollbars(double mouseX, double mouseY, int button) {
        if (warehouse.isFolded())
            return false;

        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        int x = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
        int y = screenAccessor.portablestorage$getTopPos()
                + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight);
        int bx = x + WarehouseConstants.getSidebarXOffset();
        int by = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows(), imageHeight);
        int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

        if (isContainerInterface()) {
            boolean horizontal = ModConfig.storagePosition.isHorizontal();
            if (horizontal)
                bx += iconSpacing;
            else
                by += iconSpacing;
        }

        if (ModConfig.showSmallIcons) {
            boolean horizontal = ModConfig.storagePosition.isHorizontal();
            // 排序模式、排序顺序、快速交互、智能折叠、合成补充按钮
            for (int i = 0; i < 5; i++) {
                int curX = horizontal ? bx + i * iconSpacing : bx;
                int curY = horizontal ? by : by + i * iconSpacing;

                if (mouseX >= curX && mouseX < curX + 18 && mouseY >= curY && mouseY < curY + 18) {
                    WarehouseSetting setting = WarehouseSetting.values()[i + 1]; // 偏移 1 以跳过 FOLD
                    int newVal = 0;
                    switch (setting) {
                        case SORT_MODE -> {
                            newVal = (warehouse.getSortMode() + 1) % 4;
                            warehouse.setSortMode(newVal);
                        }
                        case SORT_ORDER -> {
                            newVal = warehouse.isAscending() ? 0 : 1;
                            warehouse.setAscending(!warehouse.isAscending());
                        }
                        case QUICK_INTERACTION -> {
                            newVal = warehouse.isQuickInteraction() ? 0 : 1;
                            warehouse.setQuickInteraction(!warehouse.isQuickInteraction());
                        }
                        case SMART_COLLAPSE -> {
                            newVal = warehouse.isSmartCollapse() ? 0 : 1;
                            warehouse.setSmartCollapse(!warehouse.isSmartCollapse());
                        }
                        case CRAFT_REFILL -> {
                            newVal = warehouse.isCraftRefill() ? 0 : 1;
                            warehouse.setCraftRefill(!warehouse.isCraftRefill());
                        }
                        default -> {
                        }
                    }
                    ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(),
                            Optional.of(setting.ordinal()), Optional.of(newVal), Optional.empty(), Optional.empty()));
                    return true;
                }
            }
        }

        // 合成按钮
        boolean horizontal = ModConfig.storagePosition.isHorizontal();
        int craftingX = horizontal ? (bx + (ModConfig.showSmallIcons ? (iconSpacing * 5) : 0)) : bx;
        int craftingY = horizontal ? by : (by + (ModConfig.showSmallIcons ? (iconSpacing * 5) : 0));

        if (mouseX >= craftingX && mouseX < craftingX + 18 && mouseY >= craftingY && mouseY < craftingY + 18) {
            if (!warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
                if (screen instanceof com.portablestorage.screen.CraftingWarehouseScreen craftingScreen) {
                    craftingScreen.returnToInventoryScreen();
                } else {
                    ClientPlayNetworking.send(new OpenCraftingPayload());
                }
            }
            return true;
        }

        // +/- 按钮
        int pmX = x + WarehouseConstants.getPlusMinusXOffset();
        int pmY = y + WarehouseConstants.PLUS_MINUS_Y_OFFSET;
        if (mouseX >= pmX && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE && mouseY >= pmY
                && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
            warehouse.setVisibleRows(warehouse.getVisibleRows() - 1);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.of(-1), Optional.empty()));
            refreshScreen();
            return true;
        }
        if (mouseX >= pmX + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING
                && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE * 2 + WarehouseConstants.TINY_BUTTON_SPACING
                && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
            warehouse.setVisibleRows(warehouse.getVisibleRows() + 1);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.of(1), Optional.empty()));
            refreshScreen();
            return true;
        }

        // 滚动条
        int sx = x + WarehouseConstants.getScrollbarXOffset();
        int sy = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int sh = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
        if (mouseX >= sx && mouseX <= sx + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= sy && mouseY <= sy + sh) {
            this.isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        int usx = x + WarehouseConstants.UPGRADE_SCROLLBAR_X_OFFSET;
        if (mouseX >= usx && mouseX <= usx + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= sy && mouseY <= sy + sh) {
            this.isDraggingUpgradeScrollbar = true;
            updateUpgradeScrollFromMouse(mouseY);
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!shouldShow() || warehouse.isFolded())
            return false;

        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        int warehouseX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
        int warehouseY = screenAccessor.portablestorage$getTopPos()
                + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight);
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT
                + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;

        int upgradeColumnWidth = WarehouseConstants.getUpgradeColumnWidth();
        int delta = (int) Math.signum(scrollY);

        // 升级列滚动
        if (mouseX >= warehouseX && mouseX < warehouseX + upgradeColumnWidth && mouseY >= warehouseY
                && mouseY < warehouseY + warehouseHeight) {
            warehouse.setUpgradeScrollOffset(warehouse.getUpgradeScrollOffset() - delta);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta)));
            return true;
        }

        // 主格网滚动
        if (mouseX >= warehouseX + upgradeColumnWidth && mouseX < warehouseX + WarehouseConstants.getWarehouseWidth()
                && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            warehouse.setScrollOffset(warehouse.getScrollOffset() - delta);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.of(delta), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (shouldShow()) {
            if (this.isDraggingScrollbar) {
                updateScrollFromMouse(mouseY);
                return true;
            }
            if (this.isDraggingUpgradeScrollbar) {
                updateUpgradeScrollFromMouse(mouseY);
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        this.isDraggingUpgradeScrollbar = false;
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isVisible() && this.searchBox.isFocused()) {
            if (keyCode == 256) { // ESC
                this.searchBox.setFocused(false);
                return true;
            }
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (this.searchBox.keyPressed(event))
                return true;
            return true;
        }

        if (shouldShow() && !warehouse.isFolded()) {
            Minecraft minecraft = Minecraft.getInstance();
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (minecraft.options.keyDrop.matches(event)) {
                Slot hSlot = ((AbstractContainerScreenAccessor) screen).portablestorage$getHoveredSlot();
                if (hSlot != null && hSlot.container instanceof PlayerWarehouse && hSlot.hasItem()) {
                    boolean dropFullStack = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),
                            InputConstants.KEY_LCONTROL);
                    ClientPlayNetworking.send(new C2SDropWarehouseItemPayload(hSlot.index, dropFullStack));
                    return true;
                }
            }
        }
        return false;
    }

    private Slot getHoveredSlot(double mouseX, double mouseY) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        for (Slot slot : screen.getMenu().slots) {
            int slotX = screenAccessor.portablestorage$getLeftPos() + slot.x;
            int slotY = screenAccessor.portablestorage$getTopPos() + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                return slot;
            }
        }
        return null;
    }

    private void updateUpgradeScrollFromMouse(double mouseY) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        int scrollbarY = screenAccessor.portablestorage$getTopPos()
                + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight)
                + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE
                - WarehouseConstants.SCROLLBAR_PADDING;
        int totalUpgrades = com.portablestorage.upgrade.UpgradeRegistry.getUpgradeCount();
        int visibleRows = warehouse.getVisibleRows();
        int maxOffset = Math.max(0, totalUpgrades - visibleRows);

        if (maxOffset > 0) {
            int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalUpgrades)));
            double relativeY = Math.clamp(mouseY - scrollbarY - thumbHeight / 2.0, 0, scrollbarHeight - thumbHeight);
            int newOffset = (int) Math.round((relativeY * maxOffset) / (scrollbarHeight - thumbHeight));
            if (newOffset != warehouse.getUpgradeScrollOffset()) {
                int delta = warehouse.getUpgradeScrollOffset() - newOffset;
                warehouse.setUpgradeScrollOffset(newOffset);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta)));
            }
        }
    }

    private void updateScrollFromMouse(double mouseY) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int imageHeight = screenAccessor.portablestorage$getImageHeight();
        int scrollbarY = screenAccessor.portablestorage$getTopPos()
                + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows(), imageHeight)
                + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE
                - WarehouseConstants.SCROLLBAR_PADDING;
        int totalRows = (int) Math
                .ceil(warehouse.getSortedEntries().size() / (double) WarehouseConstants.SLOTS_PER_ROW);
        int visibleRows = warehouse.getVisibleRows();
        int maxOffset = Math.max(0, totalRows - visibleRows);

        if (maxOffset > 0) {
            int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalRows)));
            double relativeY = Math.clamp(mouseY - scrollbarY - thumbHeight / 2.0, 0, scrollbarHeight - thumbHeight);
            int newOffset = (int) Math.round((relativeY * maxOffset) / (scrollbarHeight - thumbHeight));
            if (newOffset != warehouse.getScrollOffset()) {
                int delta = warehouse.getScrollOffset() - newOffset;
                warehouse.setScrollOffset(newOffset);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.of(delta), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            }
        }
    }

    private void checkCraftRefill() {
        if (!warehouse.isCraftRefill())
            return;

        long now = System.currentTimeMillis();
        if (now - lastCraftRefillCheck < 100)
            return;
        lastCraftRefillCheck = now;

        var menu = screen.getMenu();
        Slot outputSlot = null;
        for (Slot slot : menu.slots) {
            if (slot instanceof ResultSlot) {
                outputSlot = slot;
                break;
            }
        }
        if (outputSlot == null)
            return;

        ItemStack currentOutput = outputSlot.getItem();
        boolean craftOccurred = false;
        if (!lastCraftingOutput.isEmpty()) {
            if (currentOutput.isEmpty() || !ItemStack.isSameItemSameComponents(currentOutput, lastCraftingOutput)
                    || currentOutput.getCount() < lastCraftingOutput.getCount()) {
                craftOccurred = true;
            }
        }
        lastCraftingOutput = currentOutput.copy();

        if (craftOccurred) {
            Map<ItemStack, List<Integer>> refills = new HashMap<>();
            for (Slot slot : menu.slots) {
                if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer
                        && !(slot instanceof ResultSlot)) {
                    int slotId = slot.index;
                    ItemStack currentStack = slot.getItem();
                    ItemStack lastStack = lastCraftingStacks.get(slotId);
                    if (lastStack != null && !lastStack.isEmpty()) {
                        if (currentStack.isEmpty() || (ItemStack.isSameItemSameComponents(currentStack, lastStack)
                                && currentStack.getCount() < lastStack.getCount())) {
                            boolean found = false;
                            for (ItemStack key : refills.keySet()) {
                                if (ItemStack.isSameItemSameComponents(key, lastStack)) {
                                    refills.get(key).add(slotId);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                List<Integer> list = new ArrayList<>();
                                list.add(slotId);
                                refills.put(lastStack, list);
                            }
                        }
                    }
                }
            }
            for (var entry : refills.entrySet()) {
                ClientPlayNetworking.send(new RefillPayload(entry.getValue(), entry.getKey().copy()));
            }
        }

        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer
                    && !(slot instanceof ResultSlot)) {
                lastCraftingStacks.put(slot.index, slot.getItem().copy());
            }
        }
    }

    public void removed() {
        if (shouldShow()) {
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(""),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        }
    }
}
