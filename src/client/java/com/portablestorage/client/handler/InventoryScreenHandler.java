package com.portablestorage.client.handler;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.config.YACLConfig;
import com.portablestorage.network.*;
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
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class InventoryScreenHandler {

    private final EffectRenderingInventoryScreen<InventoryMenu> screen;
    private EditBox searchBox;
    private boolean isDraggingScrollbar = false;
    private boolean isDraggingUpgradeScrollbar = false;
    private long lastSearchUpdateTime = 0;
    private String pendingSearchText = null;
    private boolean lastWorkbenchStatus = false;
    private boolean lastEnabledStatus = false;

    // Craft Refill state
    private ItemStack lastCraftingOutput = ItemStack.EMPTY;
    private final Map<Integer, ItemStack> lastCraftingStacks = new HashMap<>();
    private long lastCraftRefillCheck = 0;

    public InventoryScreenHandler(EffectRenderingInventoryScreen<InventoryMenu> screen) {
        this.screen = screen;
    }

    public boolean shouldShowWarehouse() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getAbilities().instabuild) return false;
        var warehouse = ModComponents.get(minecraft.player).getWarehouse(minecraft.player.getUUID());
        return warehouse.isEnabled();
    }

    public void init(EditBox searchBox) {
        if (!shouldShowWarehouse()) return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        this.lastWorkbenchStatus = WarehouseUtils.is3x3Enabled(player);
        this.lastEnabledStatus = warehouse.isEnabled();
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        
        // Dynamic slot positioning
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

        // Accessor for leftPos/topPos is needed
        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;
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
        
        // This is a bit tricky since we can't easily change imageHeight of the screen from here without an accessor or if it's protected.
        // In the mixin it's protected, so we need an accessor.
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

        this.searchBox = searchBox;
    }

    public void onRemoved() {
        if (shouldShowWarehouse()) {
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(""), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!shouldShowWarehouse()) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (warehouse.isFolded()) return false;

        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;
        int warehouseX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
        int warehouseY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
        
        // 1. Upgrade column scroll
        int upgradeColumnX = warehouseX;
        int upgradeColumnWidth = WarehouseConstants.getUpgradeColumnWidth();
        if (mouseX >= upgradeColumnX && mouseX < upgradeColumnX + upgradeColumnWidth && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            int delta = (int) Math.signum(scrollY);
            warehouse.setUpgradeScrollOffset(warehouse.getUpgradeScrollOffset() - delta);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta)));
            return true;
        }

        // 2. Main grid scroll
        if (mouseX >= warehouseX + upgradeColumnWidth && mouseX < warehouseX + WarehouseConstants.getWarehouseWidth() && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            int delta = (int) Math.signum(scrollY);
            warehouse.setScrollOffset(warehouse.getScrollOffset() - delta);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.of(delta), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            return true;
        }
        return false;
    }

    public void onRenderBgReturn(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shouldShowWarehouse()) return;
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        
        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;
        int x = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset(); 
        int y = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        
        com.portablestorage.mixin.client.ScreenAccessor screenAccessor2 = (com.portablestorage.mixin.client.ScreenAccessor) screen;
        WarehouseRenderer.renderBackground(graphics, x, y, mouseX, mouseY, warehouse, screenAccessor2.portablestorage$getFont());
            
        int foldX = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldY = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        WarehouseRenderer.renderSidebarButtons(graphics, foldX, foldY, x + WarehouseConstants.getSidebarXOffset(), y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows()), mouseX, mouseY, warehouse);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!shouldShowWarehouse()) return false;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        
        PlayerWarehouse warehouse = ModComponents.get(minecraft.player).getWarehouse(minecraft.player.getUUID());
        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;

        // Handle upgrade slot interactions (Right/Middle click)
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
                            com.portablestorage.upgrade.UpgradeType type = all.get(actualIndex);
                            ClientPlayNetworking.send(new C2SUpgradeInteractionPayload(type.getId(), button));
                            return true;
                        }
                    }
                }
            }
        }

        if (button == 0) { // Left click
            Slot clickedSlot = getHoveredSlot(mouseX, mouseY);
            if (clickedSlot != null && clickedSlot.container instanceof PlayerWarehouse && clickedSlot.hasItem()) {
                ItemStack stack = clickedSlot.getItem();
                net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
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
            }
        }

        boolean isShiftPressed = net.minecraft.client.gui.screens.Screen.hasShiftDown();

        if (button == 2) { // Middle click for pinning
            Slot clickedSlot = getHoveredSlot(mouseX, mouseY);
            if (clickedSlot != null && clickedSlot.container instanceof PlayerWarehouse && clickedSlot.hasItem()) {
                ClientPlayNetworking.send(new com.portablestorage.network.C2STogglePinnedPayload(clickedSlot.getContainerSlot()));
                return true;
            }
        }
        
        if (isShiftPressed && warehouse.isQuickInteraction() && !warehouse.isFolded()) {
            Slot clickedSlot = getHoveredSlot(mouseX, mouseY);
            if (clickedSlot != null && (clickedSlot.container instanceof PlayerWarehouse || clickedSlot.container instanceof net.minecraft.world.entity.player.Inventory)) {
                ClientPlayNetworking.send(new QuickTransferPayload(clickedSlot.index));
                return true;
            }
        }
        
        // Fold button and other controls
        int x = screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
        int y = screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        
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

        if (WarehouseRenderer.isOverSharingStatus(mouseX, mouseY, screenAccessor.portablestorage$getLeftPos(), screenAccessor.portablestorage$getTopPos(), warehouse)) {
            if (button == 0) {
                minecraft.setScreen(YACLConfig.createSharingManagementScreen(screen, warehouse));
                return true;
            }
        }
        
        if (!warehouse.isFolded()) {
            int bx = x + WarehouseConstants.getSidebarXOffset();
            int by = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows());
            boolean showShortcuts = ModConfig.showSmallIcons;
            int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

            if (showShortcuts) {
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18) {
                    int newVal = (warehouse.getSortMode() + 1) % 4;
                    warehouse.setSortMode(newVal);
                    ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(WarehouseSetting.SORT_MODE.ordinal()), Optional.of(newVal), Optional.empty(), Optional.empty()));
                    return true;
                }
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by + iconSpacing && mouseY < by + iconSpacing + 18) {
                    boolean newVal = !warehouse.isAscending();
                    warehouse.setAscending(newVal);
                    ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(WarehouseSetting.SORT_ORDER.ordinal()), Optional.of(newVal ? 1 : 0), Optional.empty(), Optional.empty()));
                    return true;
                }
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by + iconSpacing * 2 && mouseY < by + iconSpacing * 2 + 18) {
                    boolean newVal = !warehouse.isQuickInteraction();
                    warehouse.setQuickInteraction(newVal);
                    ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(WarehouseSetting.QUICK_INTERACTION.ordinal()), Optional.of(newVal ? 1 : 0), Optional.empty(), Optional.empty()));
                    return true;
                }
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by + iconSpacing * 3 && mouseY < by + iconSpacing * 3 + 18) {
                    boolean newVal = !warehouse.isSmartCollapse();
                    warehouse.setSmartCollapse(newVal);
                    ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(WarehouseSetting.SMART_COLLAPSE.ordinal()), Optional.of(newVal ? 1 : 0), Optional.empty(), Optional.empty()));
                    return true;
                }
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by + iconSpacing * 4 && mouseY < by + iconSpacing * 4 + 18) {
                    boolean newVal = !warehouse.isCraftRefill();
                    warehouse.setCraftRefill(newVal);
                    ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(WarehouseSetting.CRAFT_REFILL.ordinal()), Optional.of(newVal ? 1 : 0), Optional.empty(), Optional.empty()));
                    return true;
                }
            }
            
            int craftingY = by + (showShortcuts ? (iconSpacing * 5) : 0);
            if (mouseX >= bx && mouseX < bx + 18 && mouseY >= craftingY && mouseY < craftingY + 18) {
                if (!warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
                    ClientPlayNetworking.send(new OpenCraftingPayload());
                }
                return true;
            }

            int pmX = x + WarehouseConstants.getPlusMinusXOffset();
            int pmY = y + WarehouseConstants.PLUS_MINUS_Y_OFFSET;
            if (mouseX >= pmX && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
                warehouse.setVisibleRows(warehouse.getVisibleRows() - 1);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(-1), Optional.empty()));
                minecraft.setScreen(new InventoryScreen(minecraft.player));
                return true;
            }
            if (mouseX >= pmX + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE * 2 + WarehouseConstants.TINY_BUTTON_SPACING && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
                warehouse.setVisibleRows(warehouse.getVisibleRows() + 1);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(1), Optional.empty()));
                minecraft.setScreen(new InventoryScreen(minecraft.player));
                return true;
            }

            int sx = x + WarehouseConstants.getScrollbarXOffset();
            int sy = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
            int sh = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
            if (mouseX >= sx && mouseX <= sx + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= sy && mouseY <= sy + sh) {
                this.isDraggingScrollbar = true;
                this.updateScrollFromMouse(mouseY);
                return true;
            }

            int usx = x + WarehouseConstants.UPGRADE_SCROLLBAR_X_OFFSET;
            if (mouseX >= usx && mouseX <= usx + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= sy && mouseY <= sy + sh) {
                this.isDraggingUpgradeScrollbar = true;
                this.updateUpgradeScrollFromMouse(mouseY);
                return true;
            }
        }
        return false;
    }

    private Slot getHoveredSlot(double mouseX, double mouseY) {
        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;
        for (Slot slot : screen.getMenu().slots) {
            int slotX = screenAccessor.portablestorage$getLeftPos() + slot.x;
            int slotY = screenAccessor.portablestorage$getTopPos() + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                return slot;
            }
        }
        return null;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        this.isDraggingUpgradeScrollbar = false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (shouldShowWarehouse()) {
            if (this.isDraggingScrollbar) {
                this.updateScrollFromMouse(mouseY);
                return true;
            }
            if (this.isDraggingUpgradeScrollbar) {
                this.updateUpgradeScrollFromMouse(mouseY);
                return true;
            }
        }
        return false;
    }

    private void updateUpgradeScrollFromMouse(double mouseY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;
        
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
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;
        
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

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isVisible() && this.searchBox.isFocused()) {
            if (keyCode == 256) { // ESC
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true; 
        }

        if (shouldShowWarehouse()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                PlayerWarehouse warehouse = ModComponents.get(minecraft.player).getWarehouse(minecraft.player.getUUID());
                if (!warehouse.isFolded()) {
                if (minecraft.options.keyDrop.matches(keyCode, scanCode)) {
                    Slot hSlot = ((com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen).portablestorage$getHoveredSlot();
                    if (hSlot != null && hSlot.container instanceof PlayerWarehouse && hSlot.hasItem()) {
                            boolean dropFullStack = net.minecraft.client.gui.screens.Screen.hasControlDown();
                            ClientPlayNetworking.send(new C2SDropWarehouseItemPayload(hSlot.index, dropFullStack));
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shouldShowWarehouse()) return;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(minecraft.player).getWarehouse(minecraft.player.getUUID());
        
        // Handle Shift+Ctrl freezing
        boolean isPressed = net.minecraft.client.gui.screens.Screen.hasShiftDown() && net.minecraft.client.gui.screens.Screen.hasControlDown();
        if (isPressed != warehouse.isFrozen()) {
            warehouse.setFrozen(isPressed);
            ClientPlayNetworking.send(new C2SUpdateFrozenStatePayload(isPressed));
        }

        // Check for status changes to refresh screen
        if (warehouse.isEnabled() != lastEnabledStatus) {
            this.lastEnabledStatus = warehouse.isEnabled();
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            return;
        }

        if (WarehouseUtils.is3x3Enabled(minecraft.player) != lastWorkbenchStatus) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            return;
        }

        checkCraftRefill();

        // Search debouncing
        if (pendingSearchText != null && System.currentTimeMillis() - lastSearchUpdateTime > 150) {
            warehouse.setSearchText(pendingSearchText);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(pendingSearchText), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            pendingSearchText = null;
        }

        com.portablestorage.mixin.client.AbstractContainerScreenAccessor screenAccessor = (com.portablestorage.mixin.client.AbstractContainerScreenAccessor) screen;
        if (this.searchBox != null) {
            this.searchBox.setX(screenAccessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.setY(screenAccessor.portablestorage$getTopPos() + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.visible = !warehouse.isFolded();
            this.searchBox.active = !warehouse.isFolded();
        }

        com.portablestorage.mixin.client.ScreenAccessor screenAccessor2 = (com.portablestorage.mixin.client.ScreenAccessor) screen;
        WarehouseRenderer.renderPinnedOverlays(graphics, screenAccessor.portablestorage$getLeftPos(), screenAccessor.portablestorage$getTopPos(), warehouse);
        WarehouseRenderer.renderAllTooltips(graphics, screenAccessor2.portablestorage$getFont(), screenAccessor.portablestorage$getLeftPos(), screenAccessor.portablestorage$getTopPos(), mouseX, mouseY, warehouse);
        WarehouseRenderer.renderQuantityTexts(graphics, screenAccessor2.portablestorage$getFont(), screenAccessor.portablestorage$getLeftPos(), screenAccessor.portablestorage$getTopPos(), warehouse);
    }

    private void checkCraftRefill() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(minecraft.player).getWarehouse(minecraft.player.getUUID());
        if (!warehouse.isEnabled() || !warehouse.isCraftRefill()) return;

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

    public void setPendingSearchText(String text) {
        this.pendingSearchText = text;
        this.lastSearchUpdateTime = System.currentTimeMillis();
    }
}

