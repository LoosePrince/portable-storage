package com.portablestorage.client.gui;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.config.YACLConfig;
import com.portablestorage.network.*;
import com.portablestorage.util.*;
import com.portablestorage.mixin.client.AbstractContainerScreenAccessor;
import com.portablestorage.mixin.client.ScreenAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class WarehouseWidget {
    private static final ResourceLocation WAREHOUSE_SLOT_TEXTURE = com.portablestorage.PortableStorage.id("textures/gui/slot.png");

    private final AbstractContainerScreen<?> screen;
    private final PlayerWarehouse warehouse;
    
    private EditBox searchBox;
    private boolean isDraggingScrollbar = false;
    private boolean isDraggingUpgradeScrollbar = false;
    private long lastSearchUpdateTime = 0;
    private String pendingSearchText = null;
    
    // Status tracking for screen refresh
    private boolean lastWorkbenchStatus = false;
    private boolean lastEnabledStatus = false;

    // Craft Refill state
    private ItemStack lastCraftingOutput = ItemStack.EMPTY;
    private final Map<Integer, ItemStack> lastCraftingStacks = new HashMap<>();
    private long lastCraftRefillCheck = 0;

    public WarehouseWidget(AbstractContainerScreen<?> screen) {
        this.screen = screen;
        this.warehouse = ModComponents.get(Minecraft.getInstance().player).getWarehouse(Minecraft.getInstance().player.getUUID());
    }

    public boolean shouldShow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getAbilities().instabuild) return false;
        return warehouse.isEnabled();
    }

    public void init() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        
        this.lastWorkbenchStatus = WarehouseUtils.is3x3Enabled(player);
        this.lastEnabledStatus = warehouse.isEnabled();

        if (warehouse.isEnabled()) {
            int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
            updateSlotPositions();
            adjustScreenPosition(rows);
            initSearchBox();
        }

        if (this.lastWorkbenchStatus) {
            initCraftingPositions();
        }
    }

    private void initCraftingPositions() {
        int[] craftIndices = {1, 2, 46, 3, 4, 47, 48, 49, 50};
        for (int i = 0; i < craftIndices.length; i++) {
            var slot = screen.getMenu().slots.get(craftIndices[i]);
            ((com.portablestorage.mixin.accessor.SlotAccessor) slot).setX(WarehouseConstants.CRAFT_3X3_X + (i % 3) * 18);
            ((com.portablestorage.mixin.accessor.SlotAccessor) slot).setY(WarehouseConstants.CRAFT_3X3_Y + (i / 3) * 18);
        }
        
        var resultSlot = screen.getMenu().slots.get(0);
        ((com.portablestorage.mixin.accessor.SlotAccessor) resultSlot).setX(WarehouseConstants.CRAFT_RESULT_X);
        ((com.portablestorage.mixin.accessor.SlotAccessor) resultSlot).setY(WarehouseConstants.CRAFT_RESULT_Y);
    }

    private void updateSlotPositions() {
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof com.portablestorage.upgrade.UpgradeSlot upgradeSlot) {
                int index = upgradeSlot.getVisualIndex();
                int upgradeX = WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X;
                int upgradeYBase = WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y;
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot).setX(upgradeX);
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot).setY(upgradeYBase + index * WarehouseConstants.SLOT_SIZE);
            } else if (slot.container instanceof PlayerWarehouse) {
                int index = slot.getContainerSlot();
                int row = index / WarehouseConstants.SLOTS_PER_ROW;
                int col = index % WarehouseConstants.SLOTS_PER_ROW;
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot).setX(WarehouseConstants.getSlotLogicX() + col * WarehouseConstants.SLOT_SIZE);
                ((com.portablestorage.mixin.accessor.SlotAccessor) slot).setY(WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows()) + row * WarehouseConstants.SLOT_SIZE);
            }
        }
    }

    private void adjustScreenPosition(int rows) {
        int xOffset = 0;
        int yOffset = 0;

        if (ModConfig.offsetInventory) {
            StoragePosition pos = ModConfig.storagePosition;
            if (pos.isVertical()) {
                yOffset = warehouse.isFolded() ? WarehouseConstants.OFFSET_FOLDED : WarehouseConstants.OFFSET_BASE + rows * WarehouseConstants.OFFSET_PER_ROW;
            } else {
                xOffset = warehouse.isFolded() ? 0 : WarehouseConstants.getWarehouseWidth() / 2;
            }
        }

        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        if (yOffset > 0) {
            if (ModConfig.storagePosition == StoragePosition.TOP) {
                screenAccessor.portablestorage$setTopPos(screenAccessor.portablestorage$getTopPos() + yOffset);
            } else {
                screenAccessor.portablestorage$setTopPos(screenAccessor.portablestorage$getTopPos() - yOffset);
            }
        }
        if (xOffset > 0) {
            if (ModConfig.storagePosition == StoragePosition.LEFT) {
                screenAccessor.portablestorage$setLeftPos(screenAccessor.portablestorage$getLeftPos() + xOffset);
            } else {
                screenAccessor.portablestorage$setLeftPos(screenAccessor.portablestorage$getLeftPos() - xOffset);
            }
        }
        
        screenAccessor.portablestorage$setImageHeight(WarehouseConstants.VANILLA_INVENTORY_HEIGHT);

        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                if (yOffset > 0) {
                    if (ModConfig.storagePosition == StoragePosition.TOP) {
                        widget.setY(widget.getY() + yOffset);
                    } else {
                        widget.setY(widget.getY() - yOffset);
                    }
                }
                if (xOffset > 0) {
                    if (ModConfig.storagePosition == StoragePosition.LEFT) {
                        widget.setX(widget.getX() + xOffset);
                    } else {
                        widget.setX(widget.getX() - xOffset);
                    }
                }

                if (ModConfig.hideRecipeBook && widget.getY() == (screen.height / 2 - 22 - (ModConfig.storagePosition == StoragePosition.TOP ? -yOffset : yOffset))) {
                    widget.visible = false;
                    widget.active = false;
                }
            }
        }
    }

    private void initSearchBox() {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int sbX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbW = WarehouseConstants.SEARCH_BOX_WIDTH - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        int sbH = WarehouseConstants.SEARCH_BOX_HEIGHT - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        
        this.searchBox = new EditBox(((ScreenAccessor)screen).portablestorage$getFont(), sbX, sbY, sbW, sbH, Component.literal(""));
        this.searchBox.setResponder(text -> {
            this.pendingSearchText = text;
            this.lastSearchUpdateTime = System.currentTimeMillis();
        });
        this.searchBox.setEditable(true);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.portablestorage.search").withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.visible = !warehouse.isFolded();
        this.searchBox.active = !warehouse.isFolded();
        
        ((ScreenAccessor) screen).invokeAddRenderableWidget(this.searchBox);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (WarehouseUtils.is3x3Enabled(Minecraft.getInstance().player)) {
            renderCraftingBg(graphics);
        }

        if (!shouldShow()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        // 1. Background rendering
        renderBackground(graphics, mouseX, mouseY);

        // 2. Logic & Status Checks
        handleFrozenMode();
        checkRefreshNeeded();
        checkCraftRefill();
        handleSearchDebounce();

        // 3. Search Box position update
        updateSearchBoxState();

        // 4. Content rendering (Overlays, Tooltips, Quantities)
        renderOverlaysAndText(graphics, mouseX, mouseY);
    }

    private void renderCraftingBg(GuiGraphics graphics) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int cx = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.CRAFT_3X3_X - 1;
        int cy = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.CRAFT_3X3_Y - 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                graphics.blit(WAREHOUSE_SLOT_TEXTURE, cx + col * 18, cy + row * 18, 0, 0, 18, 18, 18, 18);
            }
        }
    }

    private void renderBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int x = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset(); 
        int y = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        
        WarehouseRenderer.renderBackground(graphics, x, y, mouseX, mouseY, warehouse, ((ScreenAccessor)screen).portablestorage$getFont());
            
        int foldX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        WarehouseRenderer.renderSidebarButtons(graphics, foldX, foldY, x + WarehouseConstants.getSidebarXOffset(), y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows()), mouseX, mouseY, warehouse);
    }

    private void handleFrozenMode() {
        boolean isPressed = net.minecraft.client.gui.screens.Screen.hasShiftDown() && net.minecraft.client.gui.screens.Screen.hasControlDown();
        if (isPressed != warehouse.isFrozen()) {
            warehouse.setFrozen(isPressed);
            ClientPlayNetworking.send(new C2SUpdateFrozenStatePayload(isPressed));
        }
    }

    private void checkRefreshNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (warehouse.isEnabled() != lastEnabledStatus || WarehouseUtils.is3x3Enabled(minecraft.player) != lastWorkbenchStatus) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
        }
    }

    private void handleSearchDebounce() {
        if (pendingSearchText != null && System.currentTimeMillis() - lastSearchUpdateTime > 150) {
            warehouse.setSearchText(pendingSearchText);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(pendingSearchText), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            pendingSearchText = null;
        }
    }

    private void updateSearchBoxState() {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        if (this.searchBox != null) {
            this.searchBox.setX(screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.setY(screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.visible = !warehouse.isFolded();
            this.searchBox.active = !warehouse.isFolded();
        }
    }

    private void renderOverlaysAndText(GuiGraphics graphics, int mouseX, int mouseY) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int leftPos = screenAccessor.portablestorage$getLeftPos();
        int topPos = screenAccessor.portablestorage$getTopPos();
        var font = ((ScreenAccessor)screen).portablestorage$getFont();

        WarehouseRenderer.renderPinnedOverlays(graphics, leftPos, topPos, warehouse);
        WarehouseRenderer.renderAllTooltips(graphics, font, leftPos, topPos, mouseX, mouseY, warehouse);
        WarehouseRenderer.renderQuantityTexts(graphics, font, leftPos, topPos, warehouse);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!shouldShow()) return false;
        
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;

        // 1. Upgrade slot interactions
        if (!warehouse.isFolded() && (button == 1 || button == 2)) {
            for (Slot slot : screen.getMenu().slots) {
                if (slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
                    int slotX = screenAccessor.portablestorage$getLeftPos() + slot.x;
                    int slotY = screenAccessor.portablestorage$getTopPos() + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16 && slot.hasItem()) {
                        List<com.portablestorage.upgrade.UpgradeType> all = com.portablestorage.upgrade.UpgradeRegistry.getAllUpgrades();
                        int visualIndex = ((com.portablestorage.upgrade.UpgradeSlot) slot).getVisualIndex();
                        int actualIndex = visualIndex + warehouse.getUpgradeScrollOffset();
                        if (actualIndex >= 0 && actualIndex < all.size()) {
                            ClientPlayNetworking.send(new C2SUpgradeInteractionPayload(all.get(actualIndex).getId(), button));
                            return true;
                        }
                    }
                }
            }
        }

        // 2. Main slot interactions (Left/Middle click)
        Slot clickedSlot = getHoveredSlot(mouseX, mouseY);
        if (clickedSlot != null && clickedSlot.container instanceof PlayerWarehouse && clickedSlot.hasItem()) {
            if (button == 0) { // Left click: Smart collapse search
                ItemStack stack = clickedSlot.getItem();
                var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                boolean isCollapsed = customData != null && customData.copyTag().getBoolean(WarehouseConstants.SMART_COLLAPSE_TAG);
                if (warehouse.isSmartCollapse() && warehouse.getSearchText().isEmpty() && isCollapsed) {
                    String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    String newSearch = "!" + itemId + "!";
                    if (this.searchBox != null) {
                        this.searchBox.setValue(newSearch);
                        warehouse.setSearchText(newSearch);
                        ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(newSearch), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
                    }
                    return true;
                }
            } else if (button == 2) { // Middle click: Toggle pinned
                ClientPlayNetworking.send(new C2STogglePinnedPayload(clickedSlot.getContainerSlot()));
                return true;
            }
        }

        // 3. Quick interaction (Shift + click)
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && warehouse.isQuickInteraction() && !warehouse.isFolded()) {
            if (clickedSlot != null && (clickedSlot.container instanceof PlayerWarehouse || clickedSlot.container instanceof net.minecraft.world.entity.player.Inventory)) {
                ClientPlayNetworking.send(new QuickTransferPayload(clickedSlot.index));
                return true;
            }
        }
        
        // 4. Fold button
        int foldButtonX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldButtonY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        if (mouseX >= foldButtonX && mouseX < foldButtonX + 18 && mouseY >= foldButtonY && mouseY < foldButtonY + 18) {
            if (button == 2) { // Middle click settings
                minecraft.setScreen(YACLConfig.create(screen));
                return true;
            }
            if (button == 0) { // Left click fold
                boolean newFolded = !warehouse.isFolded();
                warehouse.setFolded(newFolded);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(WarehouseSetting.FOLD.ordinal()), Optional.of(newFolded ? 1 : 0), Optional.empty(), Optional.empty()));
                if (newFolded && this.searchBox != null) this.searchBox.setFocused(false);
                minecraft.setScreen(new InventoryScreen(minecraft.player));
                return true;
            }
        }

        // 5. Sharing status
        if (WarehouseRenderer.isOverSharingStatus(mouseX, mouseY, screenAccessor.portablestorage$getLeftPos(), screenAccessor.portablestorage$getTopPos(), warehouse)) {
            if (button == 0) {
                minecraft.setScreen(YACLConfig.createSharingManagementScreen(screen, warehouse));
                return true;
            }
        }
        
        // 6. Sidebar & Scrollbars
        return handleSidebarAndScrollbars(mouseX, mouseY, button);
    }

    private boolean handleSidebarAndScrollbars(double mouseX, double mouseY, int button) {
        if (warehouse.isFolded()) return false;
        
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int x = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
        int y = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        int bx = x + WarehouseConstants.getSidebarXOffset();
        int by = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows());
        int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

        if (ModConfig.showSmallIcons) {
            // Sort mode, Order, Quick Interaction, Smart Collapse, Craft Refill buttons
            for (int i = 0; i < 5; i++) {
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by + i * iconSpacing && mouseY < by + i * iconSpacing + 18) {
                    WarehouseSetting setting = WarehouseSetting.values()[i + 1]; // Offset by 1 for FOLD
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
                    }
                    ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(setting.ordinal()), Optional.of(newVal), Optional.empty(), Optional.empty()));
                    return true;
                }
            }
        }
        
        // Crafting button
        int craftingY = by + (ModConfig.showSmallIcons ? (iconSpacing * 5) : 0);
        if (mouseX >= bx && mouseX < bx + 18 && mouseY >= craftingY && mouseY < craftingY + 18) {
            if (!warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
                ClientPlayNetworking.send(new OpenCraftingPayload());
            }
            return true;
        }

        // Plus/Minus buttons
        int pmX = x + WarehouseConstants.getPlusMinusXOffset();
        int pmY = y + WarehouseConstants.PLUS_MINUS_Y_OFFSET;
        if (mouseX >= pmX && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
            warehouse.setVisibleRows(warehouse.getVisibleRows() - 1);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(-1), Optional.empty()));
            Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            return true;
        }
        if (mouseX >= pmX + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE * 2 + WarehouseConstants.TINY_BUTTON_SPACING && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
            warehouse.setVisibleRows(warehouse.getVisibleRows() + 1);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(1), Optional.empty()));
            Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            return true;
        }

        // Scrollbars
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
        if (!shouldShow() || warehouse.isFolded()) return false;

        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int warehouseX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
        int warehouseY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
        
        int upgradeColumnWidth = WarehouseConstants.getUpgradeColumnWidth();
        int delta = (int) Math.signum(scrollY);

        // 1. Upgrade column scroll
        if (mouseX >= warehouseX && mouseX < warehouseX + upgradeColumnWidth && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            warehouse.setUpgradeScrollOffset(warehouse.getUpgradeScrollOffset() - delta);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta)));
            return true;
        }

        // 2. Main grid scroll
        if (mouseX >= warehouseX + upgradeColumnWidth && mouseX < warehouseX + WarehouseConstants.getWarehouseWidth() && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            warehouse.setScrollOffset(warehouse.getScrollOffset() - delta);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.of(delta), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
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
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true; 
        }

        if (shouldShow() && !warehouse.isFolded()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options.keyDrop.matches(keyCode, scanCode)) {
                Slot hSlot = ((AbstractContainerScreenAccessor) screen).portablestorage$getHoveredSlot();
                if (hSlot != null && hSlot.container instanceof PlayerWarehouse && hSlot.hasItem()) {
                    boolean dropFullStack = net.minecraft.client.gui.screens.Screen.hasControlDown();
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
        int scrollbarY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
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
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta)));
            }
        }
    }

    private void updateScrollFromMouse(double mouseY) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) screen;
        int scrollbarY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
        int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / (double) WarehouseConstants.SLOTS_PER_ROW);
        int visibleRows = warehouse.getVisibleRows();
        int maxOffset = Math.max(0, totalRows - visibleRows);
        
        if (maxOffset > 0) {
            int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalRows)));
            double relativeY = Math.clamp(mouseY - scrollbarY - thumbHeight / 2.0, 0, scrollbarHeight - thumbHeight);
            int newOffset = (int) Math.round((relativeY * maxOffset) / (scrollbarHeight - thumbHeight));
            if (newOffset != warehouse.getScrollOffset()) {
                int delta = warehouse.getScrollOffset() - newOffset;
                warehouse.setScrollOffset(newOffset);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.of(delta), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            }
        }
    }

    private void checkCraftRefill() {
        if (!warehouse.isCraftRefill()) return;

        long now = System.currentTimeMillis();
        if (now - lastCraftRefillCheck < 100) return;
        lastCraftRefillCheck = now;

        var menu = screen.getMenu();
        Slot outputSlot = null;
        for (Slot slot : menu.slots) {
            if (slot instanceof ResultSlot) {
                outputSlot = slot;
                break;
            }
        }
        if (outputSlot == null) return;
        
        ItemStack currentOutput = outputSlot.getItem();
        boolean craftOccurred = false;
        if (!lastCraftingOutput.isEmpty()) {
            if (currentOutput.isEmpty() || !ItemStack.isSameItemSameComponents(currentOutput, lastCraftingOutput) || currentOutput.getCount() < lastCraftingOutput.getCount()) {
                craftOccurred = true;
            }
        }
        lastCraftingOutput = currentOutput.copy();

        if (craftOccurred) {
            Map<ItemStack, List<Integer>> refills = new HashMap<>();
            for (Slot slot : menu.slots) {
                if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer && !(slot instanceof ResultSlot)) {
                    int slotId = slot.index;
                    ItemStack currentStack = slot.getItem();
                    ItemStack lastStack = lastCraftingStacks.get(slotId);
                    if (lastStack != null && !lastStack.isEmpty()) {
                        if (currentStack.isEmpty() || (ItemStack.isSameItemSameComponents(currentStack, lastStack) && currentStack.getCount() < lastStack.getCount())) {
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
            if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer && !(slot instanceof ResultSlot)) {
                lastCraftingStacks.put(slot.index, slot.getItem().copy());
            }
        }
    }

    public void removed() {
        if (shouldShow()) {
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(""), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        }
    }
}
