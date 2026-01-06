package com.portablestorage.mixin.client;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.config.YACLConfig;
import com.portablestorage.util.StoragePosition;
import com.portablestorage.util.WarehouseSetting;
import com.portablestorage.network.*;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.List;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    @Unique
    private static final ResourceLocation WAREHOUSE_GUI_TEXTURE = com.portablestorage.PortableStorage.id("textures/gui/gui.png");
    @Unique
    private static final ResourceLocation WAREHOUSE_SLOT_TEXTURE = com.portablestorage.PortableStorage.id("textures/gui/slot.png");

    @Unique
    private EditBox searchBox;

    @Unique
    private boolean isDraggingScrollbar = false;
    @Unique
    private boolean isDraggingUpgradeScrollbar = false;

    @Unique
    private long lastSearchUpdateTime = 0;
    @Unique
    private String pendingSearchText = null;

    @Unique
    private boolean lastWorkbenchStatus = false;
    @Unique
    private boolean lastEnabledStatus = false;

    public InventoryScreenMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Unique
    private boolean shouldShowWarehouse() {
        // 核心修复：如果是创造模式，始终不显示仓库，避免与创造模式复杂的 UI 重叠
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.player.getAbilities().instabuild) return false;
        var warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        return warehouse.isEnabled();
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInit(CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        this.lastWorkbenchStatus = com.portablestorage.util.WarehouseUtils.is3x3Enabled(player);
        this.lastEnabledStatus = warehouse.isEnabled();
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        
        // 动态更新槽位位置以匹配当前配置
        for (Slot slot : this.menu.slots) {
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

        if (yOffset > 0) {
            if (ModConfig.storagePosition == StoragePosition.TOP) {
                this.topPos += yOffset;
            } else {
            this.topPos -= yOffset; 
            }
        }
        if (xOffset > 0) {
            if (ModConfig.storagePosition == StoragePosition.LEFT) {
                this.leftPos += xOffset;
            } else {
                this.leftPos -= xOffset;
            }
        }
        
        this.imageHeight = WarehouseConstants.VANILLA_INVENTORY_HEIGHT; 

        for (GuiEventListener child : this.children()) {
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

                if (ModConfig.hideRecipeBook && widget.getY() == (this.height / 2 - 22 - (ModConfig.storagePosition == StoragePosition.TOP ? -yOffset : yOffset))) {
                    widget.visible = false;
                    widget.active = false;
                }
            }
        }

        int sbX = this.leftPos + WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbY = this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbW = WarehouseConstants.SEARCH_BOX_WIDTH - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        int sbH = WarehouseConstants.SEARCH_BOX_HEIGHT - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        
        this.searchBox = new EditBox(this.font, sbX, sbY, sbW, sbH, Component.literal(""));
        this.searchBox.setResponder(text -> {
            this.pendingSearchText = text;
            this.lastSearchUpdateTime = System.currentTimeMillis();
            // 不再立即在客户端设置搜索文本，防止与服务端索引不一致导致取出错误物品
        });
        this.searchBox.setEditable(true);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.portablestorage.search").withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.visible = !warehouse.isFolded();
        this.searchBox.active = !warehouse.isFolded();
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public void removed() {
        if (shouldShowWarehouse()) {
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(""), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        }
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!shouldShowWarehouse()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        var player = Minecraft.getInstance().player;
        if (player == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (warehouse.isFolded()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        int warehouseX = this.leftPos + WarehouseConstants.getWarehouseXOffset();
        int warehouseY = this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
        
        // 1. 升级列滚动
        int upgradeColumnX = warehouseX;
        int upgradeColumnWidth = WarehouseConstants.getUpgradeColumnWidth();
        if (mouseX >= upgradeColumnX && mouseX < upgradeColumnX + upgradeColumnWidth && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            int delta = (int) Math.signum(scrollY);
            warehouse.setUpgradeScrollOffset(warehouse.getUpgradeScrollOffset() - delta);
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta)));
            return true;
        }

        // 2. 主格网滚动
        if (mouseX >= warehouseX + upgradeColumnWidth && mouseX < warehouseX + WarehouseConstants.getWarehouseWidth() && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            int delta = (int) Math.signum(scrollY);
            warehouse.setScrollOffset(warehouse.getScrollOffset() - delta); // 客户端立即响应
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.of(delta), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Inject(method = "renderBg", at = @At("HEAD"))
    protected void onRenderBgHead(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        this.imageHeight = WarehouseConstants.VANILLA_INVENTORY_HEIGHT;
    }

    @Inject(method = "renderBg", at = @At("RETURN"))
    protected void onRenderBgReturn(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        
        int x = this.leftPos + WarehouseConstants.getWarehouseXOffset(); 
        int y = this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
        
        // 使用封装好的渲染逻辑
        WarehouseRenderer.renderBackground(graphics, x, y, mouseX, mouseY, warehouse, this.font);
            
        int foldX = this.leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldY = this.topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        WarehouseRenderer.renderSidebarButtons(graphics, foldX, foldY, x + WarehouseConstants.getSidebarXOffset(), y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows()), mouseX, mouseY, warehouse);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检测仓库槽位的点击
        if (shouldShowWarehouse() && this.minecraft != null && this.minecraft.player != null) {
            PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
            
            // 处理升级槽位的右键和中键交互
            if (!warehouse.isFolded() && (button == 1 || button == 2)) {
                for (Slot slot : this.menu.slots) {
                    if (slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
                        int slotX = this.leftPos + slot.x;
                        int slotY = this.topPos + slot.y;
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

            if (button == 0) { // 左键
                Slot clickedSlot = null;
                for (Slot slot : this.menu.slots) {
                    int slotX = this.leftPos + slot.x;
                    int slotY = this.topPos + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                        clickedSlot = slot;
                        break;
                    }
                }
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
                        return true; // 拦截，不继续处理 Shift 点击
                    }
                }
            }

            // 2. 检测 Shift 键状态
            boolean isShiftPressed = net.minecraft.client.gui.screens.Screen.hasShiftDown();

            if (button == 2) { // 中键置顶
                Slot clickedSlot = null;
                for (Slot slot : this.menu.slots) {
                    int slotX = this.leftPos + slot.x;
                    int slotY = this.topPos + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                        clickedSlot = slot;
                        break;
                    }
                }
                if (clickedSlot != null && clickedSlot.container instanceof PlayerWarehouse && clickedSlot.hasItem()) {
                    ClientPlayNetworking.send(new com.portablestorage.network.C2STogglePinnedPayload(clickedSlot.getContainerSlot()));
                    return true;
                }
            }
            
            if (isShiftPressed && warehouse.isQuickInteraction() && !warehouse.isFolded()) {
                // 手动查找被点击的槽位
                Slot clickedSlot = null;
                for (Slot slot : this.menu.slots) {
                    int slotX = this.leftPos + slot.x;
                    int slotY = this.topPos + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                        clickedSlot = slot;
                        break;
                    }
                }

                // 检查是否点击在仓库槽位或背包槽位上
                if (clickedSlot != null && (clickedSlot.container instanceof PlayerWarehouse || clickedSlot.container instanceof net.minecraft.world.entity.player.Inventory)) {
                    // 发送快速转移网络包
                    ClientPlayNetworking.send(new QuickTransferPayload(clickedSlot.index));
                    return true;
                }
            }
        }
        
        if (shouldShowWarehouse()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
                int x = this.leftPos + WarehouseConstants.getWarehouseXOffset();
                int y = this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
                
                int foldButtonX = this.leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
                int foldButtonY = this.topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;

                if (mouseX >= foldButtonX && mouseX < foldButtonX + 18 && mouseY >= foldButtonY && mouseY < foldButtonY + 18) {
                    if (button == 2) { // 中键
                        this.minecraft.setScreen(YACLConfig.create(this));
                        return true;
                    }
                    if (button == 0) { // 左键
                        boolean newFolded = !warehouse.isFolded();
                        warehouse.setFolded(newFolded);
                         ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.of(WarehouseSetting.FOLD.ordinal()), Optional.of(newFolded ? 1 : 0), Optional.empty(), Optional.empty()));
                        if (newFolded && this.searchBox != null) this.searchBox.setFocused(false);
                        this.minecraft.setScreen(new InventoryScreen(player));
                        return true;
                    }
                }

                if (com.portablestorage.util.WarehouseRenderer.isOverSharingStatus(mouseX, mouseY, this.leftPos, this.topPos, warehouse)) {
                    if (button == 0) { // 左键
                        this.minecraft.setScreen(YACLConfig.createSharingManagementScreen(this, warehouse));
                        return true;
                    }
                }
                
                int bx = x + WarehouseConstants.getSidebarXOffset();
                int by = y + WarehouseConstants.getSidebarYOffset(warehouse.getVisibleRows());
                boolean showShortcuts = ModConfig.showSmallIcons;
                int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

                if (!warehouse.isFolded()) {
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
                        this.minecraft.setScreen(new InventoryScreen(player));
                        return true;
                    }
                    if (mouseX >= pmX + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE * 2 + WarehouseConstants.TINY_BUTTON_SPACING && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
                        warehouse.setVisibleRows(warehouse.getVisibleRows() + 1);
                        ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(1), Optional.empty()));
                        this.minecraft.setScreen(new InventoryScreen(player));
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

                    // 升级槽位滚动条点击
                    int usx = x + WarehouseConstants.UPGRADE_SCROLLBAR_X_OFFSET;
                    if (mouseX >= usx && mouseX <= usx + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= sy && mouseY <= sy + sh) {
                        this.isDraggingUpgradeScrollbar = true;
                        this.updateUpgradeScrollFromMouse(mouseY);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        this.isDraggingUpgradeScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Unique
    private void updateUpgradeScrollFromMouse(double mouseY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        
        int scrollbarY = this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
        
        int totalUpgrades = com.portablestorage.upgrade.UpgradeRegistry.getUpgradeCount();
        int visibleRows = warehouse.getVisibleRows();
        int maxOffset = Math.max(0, totalUpgrades - visibleRows);
        
        if (maxOffset > 0) {
            int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalUpgrades)));
            double relativeY = Math.clamp(mouseY - scrollbarY - thumbHeight / 2.0, 0, scrollbarHeight - thumbHeight);
            int newOffset = (int) Math.round((relativeY * maxOffset) / (scrollbarHeight - thumbHeight));
            if (newOffset != warehouse.getUpgradeScrollOffset()) {
                int currentOffset = warehouse.getUpgradeScrollOffset();
                int delta = newOffset - currentOffset;
                warehouse.setUpgradeScrollOffset(newOffset);
                ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta)));
            }
        }
    }

    @Unique
    private void updateScrollFromMouse(double mouseY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        
        int scrollbarY = this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SCROLLBAR_Y_OFFSET;
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isVisible() && this.searchBox.isFocused()) {
            if (keyCode == 256) { // ESC
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true; 
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Unique private ItemStack lastCraftingOutput = ItemStack.EMPTY;
    @Unique private final java.util.Map<Integer, ItemStack> lastCraftingStacks = new java.util.HashMap<>();
    @Unique private long lastCraftRefillCheck = 0;

    @Unique
    private void checkCraftRefill() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
        if (!warehouse.isEnabled() || !warehouse.isCraftRefill()) return;

        long now = System.currentTimeMillis();
        if (now - lastCraftRefillCheck < 100) return;
        lastCraftRefillCheck = now;

        net.minecraft.world.inventory.AbstractContainerMenu menu = this.getMenu();
        
        // 1. 检测合成是否发生
        net.minecraft.world.inventory.Slot outputSlot = null;
        for (Slot slot : menu.slots) {
            if (slot instanceof net.minecraft.world.inventory.ResultSlot) {
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

        // 2. 如果发生合成，检测消耗并补货
        if (craftOccurred) {
            java.util.Map<ItemStack, java.util.List<Integer>> refills = new java.util.HashMap<>();
            
            for (Slot slot : menu.slots) {
                // 动态识别合成输入槽位 (CraftingContainer 且不是结果槽位)
                if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer && !(slot instanceof ResultSlot)) {
                    int slotId = slot.index;
                    ItemStack currentStack = slot.getItem();
                    ItemStack lastStack = lastCraftingStacks.get(slotId);

                    if (lastStack != null && !lastStack.isEmpty()) {
                        if (currentStack.isEmpty() || (ItemStack.isSameItemSameComponents(currentStack, lastStack) && currentStack.getCount() < lastStack.getCount())) {
                            // 按物品类型分组
                            boolean found = false;
                            for (ItemStack key : refills.keySet()) {
                                if (ItemStack.isSameItemSameComponents(key, lastStack)) {
                                    refills.get(key).add(slotId);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                java.util.List<Integer> list = new java.util.ArrayList<>();
                                list.add(slotId);
                                refills.put(lastStack, list);
                            }
                        }
                    }
                }
            }
            
            // 发送分组后的请求
            for (var entry : refills.entrySet()) {
                ClientPlayNetworking.send(new RefillPayload(entry.getValue(), entry.getKey().copy()));
            }
        }

        // 3. 更新输入缓存
        for (Slot slot : menu.slots) {
            if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer && !(slot instanceof ResultSlot)) {
                lastCraftingStacks.put(slot.index, slot.getItem().copy());
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void renderWarehouseContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        
        // 检查状态变化，若发生变化（如死亡禁用、激活等）则刷新屏幕以重构布局
        if (warehouse.isEnabled() != lastEnabledStatus) {
            this.lastEnabledStatus = warehouse.isEnabled();
            this.minecraft.setScreen(new InventoryScreen(player));
            return;
        }

        if (com.portablestorage.util.WarehouseUtils.is3x3Enabled(player) != lastWorkbenchStatus) {
            this.minecraft.setScreen(new InventoryScreen(player));
            return;
        }

        if (!shouldShowWarehouse()) return;

        checkCraftRefill();

        // 处理搜索框防抖
        if (pendingSearchText != null && System.currentTimeMillis() - lastSearchUpdateTime > 150) {
            warehouse.setSearchText(pendingSearchText); // 此时再更新本地显示
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(Optional.empty(), Optional.of(pendingSearchText), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            pendingSearchText = null;
        }

        if (this.searchBox != null) {
            this.searchBox.setX(this.leftPos + WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.getSearchBoxXOffset() + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.setY(this.topPos + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.visible = !warehouse.isFolded();
            this.searchBox.active = !warehouse.isFolded();
        }

        // 使用封装好的 Tooltip 和数量渲染
        WarehouseRenderer.renderPinnedOverlays(graphics, this.leftPos, this.topPos, warehouse);
        WarehouseRenderer.renderAllTooltips(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY, warehouse);
        WarehouseRenderer.renderQuantityTexts(graphics, this.font, this.leftPos, this.topPos, warehouse);
    }
}
