package com.portablestorage.mixin.client;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.config.YACLConfig;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    @Unique
    private static final ResourceLocation WAREHOUSE_GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath("portablestorage", "textures/gui/gui.png");
    @Unique
    private static final ResourceLocation WAREHOUSE_SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath("portablestorage", "textures/gui/slot.png");

    @Unique
    private EditBox searchBox;

    @Unique
    private boolean isDraggingScrollbar = false;

    public InventoryScreenMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Unique
    private boolean shouldShowWarehouse() {
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
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        
        int yOffset = ModConfig.offsetInventory ? (warehouse.isFolded() ? WarehouseConstants.OFFSET_FOLDED : WarehouseConstants.OFFSET_BASE + rows * WarehouseConstants.OFFSET_PER_ROW) : 0; 
        if (yOffset > 0) {
            this.topPos -= yOffset; 
        }
        
        this.imageHeight = WarehouseConstants.VANILLA_INVENTORY_HEIGHT; 

        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                if (yOffset > 0) {
                    widget.setY(widget.getY() - yOffset);
                }

                if (ModConfig.hideRecipeBook && widget.getY() == (this.height / 2 - 22 - yOffset)) {
                    widget.visible = false;
                    widget.active = false;
                }
            }
        }

        int sbX = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbY = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET;
        int sbW = WarehouseConstants.SEARCH_BOX_WIDTH - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        int sbH = WarehouseConstants.SEARCH_BOX_HEIGHT - WarehouseConstants.SEARCH_BOX_INNER_OFFSET * 2;
        
        this.searchBox = new EditBox(this.font, sbX, sbY, sbW, sbH, Component.literal(""));
        this.searchBox.setResponder(text -> ClientPlayNetworking.send(new SearchPayload(text)));
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
            ClientPlayNetworking.send(new SearchPayload(""));
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

        int warehouseX = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET;
        int warehouseY = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
        if (mouseX >= warehouseX && mouseX < warehouseX + WarehouseConstants.WAREHOUSE_WIDTH && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
            ClientPlayNetworking.send(new ScrollPayload((int) Math.signum(scrollY)));
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
        
        int x = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET; 
        int y = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
        
        // 使用封装好的渲染逻辑
        WarehouseRenderer.renderBackground(graphics, x, y, mouseX, mouseY, warehouse, this.font);
            
        int foldX = this.leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
        int foldY = this.topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
        WarehouseRenderer.renderSidebarButtons(graphics, foldX, foldY, x + WarehouseConstants.SIDEBAR_X_OFFSET, y, mouseX, mouseY, warehouse);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检测仓库槽位的点击
        if (shouldShowWarehouse() && this.minecraft != null && this.minecraft.player != null && button == 0) { // 左键
            PlayerWarehouse warehouse = ModComponents.get(this.minecraft.player).getWarehouse(this.minecraft.player.getUUID());
            
            // 1. 处理折叠项的点击（展开搜索）
            if (!net.minecraft.client.gui.screens.Screen.hasShiftDown() && !warehouse.isFolded()) {
                Slot clickedSlot = null;
                for (Slot slot : this.menu.slots) {
                    int slotX = this.leftPos + slot.x;
                    int slotY = this.topPos + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
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
                            ClientPlayNetworking.send(new SearchPayload(newSearch));
                        }
                        return true;
                    }
                }
            }

            // 2. 检测 Shift 键状态
            boolean isShiftPressed = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            
            if (isShiftPressed && warehouse.isQuickInteraction() && !warehouse.isFolded()) {
                // 手动查找被点击的槽位
                Slot clickedSlot = null;
                for (Slot slot : this.menu.slots) {
                    int slotX = this.leftPos + slot.x;
                    int slotY = this.topPos + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                        clickedSlot = slot;
                        break;
            }
        }

                // 检查是否点击在仓库槽位上
                if (clickedSlot != null && clickedSlot.container instanceof PlayerWarehouse) {
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
                int x = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET;
                int y = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
                
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
                         ClientPlayNetworking.send(new UpdateSettingsPayload(WarehouseSetting.FOLD, newFolded ? 1 : 0));
                        if (newFolded && this.searchBox != null) this.searchBox.setFocused(false);
                        this.minecraft.setScreen(new InventoryScreen(player));
                        return true;
                    }
                }
                
                int bx = x + WarehouseConstants.SIDEBAR_X_OFFSET;
                boolean showShortcuts = ModConfig.showSmallIcons;
                int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

                if (!warehouse.isFolded()) {
                    if (showShortcuts) {
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
                        if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing * 3 && mouseY < y + iconSpacing * 3 + 18) {
                            ClientPlayNetworking.send(new UpdateSettingsPayload(WarehouseSetting.SMART_COLLAPSE, warehouse.isSmartCollapse() ? 0 : 1));
                            return true;
                        }
                        if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing * 4 && mouseY < y + iconSpacing * 4 + 18) {
                            ClientPlayNetworking.send(new UpdateSettingsPayload(WarehouseSetting.CRAFT_REFILL, warehouse.isCraftRefill() ? 0 : 1));
                            return true;
                        }
                    }
                    
                    int craftingY = y + (showShortcuts ? (iconSpacing * 5) : 0);
                    if (mouseX >= bx && mouseX < bx + 18 && mouseY >= craftingY && mouseY < craftingY + 18) {
                        ClientPlayNetworking.send(new OpenCraftingPayload());
                        return true;
                    }

                    int pmX = x + WarehouseConstants.PLUS_MINUS_X_OFFSET;
                    int pmY = y + WarehouseConstants.PLUS_MINUS_Y_OFFSET;
                    if (mouseX >= pmX && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
                        ClientPlayNetworking.send(new ChangeRowsPayload(-1));
                        this.minecraft.setScreen(new InventoryScreen(player));
                        return true;
                    }
                    if (mouseX >= pmX + WarehouseConstants.TINY_BUTTON_SIZE + WarehouseConstants.TINY_BUTTON_SPACING && mouseX < pmX + WarehouseConstants.TINY_BUTTON_SIZE * 2 + WarehouseConstants.TINY_BUTTON_SPACING && mouseY >= pmY && mouseY < pmY + WarehouseConstants.TINY_BUTTON_SIZE) {
                        ClientPlayNetworking.send(new ChangeRowsPayload(1));
                        this.minecraft.setScreen(new InventoryScreen(player));
                        return true;
                    }

                    int sx = x + WarehouseConstants.SCROLLBAR_X_OFFSET;
                    int sy = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
                    int sh = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
                    if (mouseX >= sx && mouseX <= sx + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= sy && mouseY <= sy + sh) {
                        this.isDraggingScrollbar = true;
                        this.updateScrollFromMouse(mouseY);
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScrollbar && shouldShowWarehouse()) {
            this.updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Unique
    private void updateScrollFromMouse(double mouseY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        
        int scrollbarY = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET + WarehouseConstants.SCROLLBAR_Y_OFFSET;
        int scrollbarHeight = warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
        
        int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / (double) WarehouseConstants.SLOTS_PER_ROW);
        int visibleRows = warehouse.getVisibleRows();
        int maxOffset = Math.max(0, totalRows - visibleRows);
        
        if (maxOffset > 0) {
            int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalRows)));
            double relativeY = Math.clamp(mouseY - scrollbarY - thumbHeight / 2.0, 0, scrollbarHeight - thumbHeight);
            int newOffset = (int) Math.round((relativeY * maxOffset) / (scrollbarHeight - thumbHeight));
            if (newOffset != warehouse.getScrollOffset()) ClientPlayNetworking.send(new ScrollPayload(warehouse.getScrollOffset() - newOffset));
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
        net.minecraft.world.inventory.Slot outputSlot = menu.getSlot(0);
        ItemStack currentOutput = outputSlot.getItem();
        
        boolean craftOccurred = false;
        if (!lastCraftingOutput.isEmpty()) {
            if (currentOutput.isEmpty() || !ItemStack.isSameItemSameComponents(currentOutput, lastCraftingOutput) || currentOutput.getCount() < lastCraftingOutput.getCount()) {
                craftOccurred = true;
            }
        }
        lastCraftingOutput = currentOutput.copy();

        // 2. 如果发生合成，检测消耗并补货
        int[] craftInputSlots = {1, 2, 3, 4, 46, 47, 48, 49, 50};
        if (craftOccurred) {
            java.util.Map<ItemStack, java.util.List<Integer>> refills = new java.util.HashMap<>();
            
            for (int slotId : craftInputSlots) {
                if (slotId >= menu.slots.size()) continue;
                net.minecraft.world.inventory.Slot slot = menu.getSlot(slotId);
                ItemStack currentStack = slot.getItem();
                ItemStack lastStack = lastCraftingStacks.get(slotId);

                if (lastStack != null && !lastStack.isEmpty()) {
                    if (currentStack.isEmpty() || (ItemStack.isSameItemSameComponents(currentStack, lastStack) && currentStack.getCount() < lastStack.getCount())) {
                        // 按物品类型分组（使用 ItemStack 作为键，需要自定义比较逻辑或转换为唯一键）
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
            
            // 发送分组后的请求
            for (var entry : refills.entrySet()) {
                ClientPlayNetworking.send(new RefillPayload(entry.getValue(), entry.getKey().copy()));
            }
        }

        // 3. 更新输入缓存
        for (int slotId : craftInputSlots) {
            if (slotId < menu.slots.size()) {
                lastCraftingStacks.put(slotId, menu.getSlot(slotId).getItem().copy());
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void renderWarehouseContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        
        checkCraftRefill();
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());

        if (this.searchBox != null) {
            this.searchBox.setX(this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.setY(this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.visible = !warehouse.isFolded();
            this.searchBox.active = !warehouse.isFolded();
        }

        // 使用封装好的 Tooltip 和数量渲染
        WarehouseRenderer.renderAllTooltips(graphics, this.font, this.leftPos, this.topPos, mouseX, mouseY, warehouse);
        WarehouseRenderer.renderQuantityTexts(graphics, this.font, this.leftPos, this.topPos, warehouse);
    }
}
