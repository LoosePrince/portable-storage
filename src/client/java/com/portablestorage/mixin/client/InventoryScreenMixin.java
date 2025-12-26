package com.portablestorage.mixin.client;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.mixin.accessor.SlotAccessor;
import com.portablestorage.network.ChangeRowsPayload;
import com.portablestorage.network.ScrollPayload;
import com.portablestorage.network.SearchPayload;
import com.portablestorage.network.UpdateSettingsPayload;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseRenderer;
import com.portablestorage.util.WarehouseUtils;
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
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

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
        return this.minecraft != null && this.minecraft.player != null && !this.minecraft.player.getAbilities().instabuild;
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInit(CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!shouldShowWarehouse()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        var player = Minecraft.getInstance().player;
        if (player == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
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
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        int rows = warehouse.isFolded() ? 0 : warehouse.getVisibleRows();
        
        int warehouseHeight = WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + rows * WarehouseConstants.SLOT_SIZE;
        
        int x = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET; 
        int y = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
        
        if (!warehouse.isFolded()) {
            WarehouseRenderer.drawNinePatch(graphics, WAREHOUSE_GUI_TEXTURE, x, y, WarehouseConstants.WAREHOUSE_WIDTH, warehouseHeight, WarehouseConstants.WAREHOUSE_CORNER_SIZE);
            
            graphics.fill(x + WarehouseConstants.SEARCH_BOX_X_OFFSET, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET, x + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_WIDTH, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BG_COLOR);
            graphics.fill(x + WarehouseConstants.SEARCH_BOX_X_OFFSET, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET, x + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_WIDTH, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET + 1, WarehouseConstants.SEARCH_BOX_BORDER_DARK);
            graphics.fill(x + WarehouseConstants.SEARCH_BOX_X_OFFSET, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET, x + WarehouseConstants.SEARCH_BOX_X_OFFSET + 1, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_DARK);
            graphics.fill(x + WarehouseConstants.SEARCH_BOX_X_OFFSET, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_HEIGHT - 1, x + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_WIDTH, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);
            graphics.fill(x + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_WIDTH - 1, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET, x + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_WIDTH, y + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_HEIGHT, WarehouseConstants.SEARCH_BOX_BORDER_LIGHT);

            WarehouseRenderer.renderPlusMinusButtons(graphics, this.font, x + WarehouseConstants.PLUS_MINUS_X_OFFSET, y + WarehouseConstants.PLUS_MINUS_Y_OFFSET, mouseX, mouseY);
            
            int slotStartX = this.leftPos + WarehouseConstants.SLOT_LOGIC_X + WarehouseConstants.SLOT_VISUAL_OFFSET; 
            int slotStartY = this.topPos + WarehouseConstants.SLOT_LOGIC_Y_BASE + WarehouseConstants.SLOT_VISUAL_OFFSET;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                    graphics.blit(WAREHOUSE_SLOT_TEXTURE, slotStartX + col * WarehouseConstants.SLOT_SIZE, slotStartY + row * WarehouseConstants.SLOT_SIZE, 0, 0, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE, WarehouseConstants.SLOT_SIZE);
                }
            }

            int scrollbarX = x + WarehouseConstants.SCROLLBAR_X_OFFSET; 
            int scrollbarY = y + WarehouseConstants.SCROLLBAR_Y_OFFSET;
            int scrollbarHeight = rows * WarehouseConstants.SLOT_SIZE - WarehouseConstants.SCROLLBAR_PADDING;
            
            if (scrollbarHeight > 0) {
                graphics.fill(scrollbarX, scrollbarY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, WarehouseConstants.SCROLLBAR_BG_COLOR);
                int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / (double) WarehouseConstants.SLOTS_PER_ROW);
                int thumbHeight = (totalRows <= rows) ? scrollbarHeight : Math.max(10, (int) (scrollbarHeight * ((float) rows / totalRows)));
                int maxOffset = Math.max(0, totalRows - rows);
                int thumbY = scrollbarY + (maxOffset == 0 ? 0 : (warehouse.getScrollOffset() * (scrollbarHeight - thumbHeight) / maxOffset));
                int thumbColor = (isDraggingScrollbar || (mouseX >= scrollbarX && mouseX <= scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY <= thumbY + thumbHeight)) ? WarehouseConstants.SCROLLBAR_THUMB_HOVER_COLOR : WarehouseConstants.SCROLLBAR_THUMB_COLOR;
                graphics.fill(scrollbarX, thumbY, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
                graphics.fill(scrollbarX - 1, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY, WarehouseConstants.SCROLLBAR_BORDER_LIGHT); 
                graphics.fill(scrollbarX - 1, thumbY, scrollbarX, thumbY + thumbHeight, WarehouseConstants.SCROLLBAR_BORDER_LIGHT); 
                graphics.fill(scrollbarX, thumbY + thumbHeight, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight + 1, WarehouseConstants.SCROLLBAR_BORDER_DARK); 
                graphics.fill(scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH, thumbY - 1, scrollbarX + WarehouseConstants.SCROLLBAR_WIDTH + 1, thumbY + thumbHeight, WarehouseConstants.SCROLLBAR_BORDER_DARK); 
            }
        }

        WarehouseRenderer.renderSidebarButtons(graphics, this.leftPos, this.topPos, x + WarehouseConstants.SIDEBAR_X_OFFSET, y, mouseX, mouseY, warehouse);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (shouldShowWarehouse()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
                int x = this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET;
                int y = this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET;
                
                int foldButtonX = this.leftPos + WarehouseConstants.FOLD_BUTTON_X_OFFSET;
                int foldButtonY = this.topPos + WarehouseConstants.FOLD_BUTTON_Y_OFFSET;
                if (mouseX >= foldButtonX && mouseX < foldButtonX + 18 && mouseY >= foldButtonY && mouseY < foldButtonY + 18) {
                    boolean newFolded = !warehouse.isFolded();
                    warehouse.setFolded(newFolded);
                    ClientPlayNetworking.send(new UpdateSettingsPayload(0, newFolded ? 1 : 0));
                    if (newFolded && this.searchBox != null) this.searchBox.setFocused(false);
                    this.minecraft.setScreen(new InventoryScreen(player));
                    return true;
                }
                
                int bx = x + WarehouseConstants.SIDEBAR_X_OFFSET;
                int iconSpacing = WarehouseConstants.SIDEBAR_BUTTON_SIZE + WarehouseConstants.SIDEBAR_BUTTON_SPACING;

                if (!warehouse.isFolded()) {
                    if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y && mouseY < y + 18) {
                        ClientPlayNetworking.send(new UpdateSettingsPayload(1, (warehouse.getSortMode() + 1) % 4));
                        return true;
                    }
                    if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing && mouseY < y + iconSpacing + 18) {
                        ClientPlayNetworking.send(new UpdateSettingsPayload(2, warehouse.isAscending() ? 0 : 1));
                        return true;
                    }
                    if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y + iconSpacing * 2 && mouseY < y + iconSpacing * 2 + 18) {
                        ClientPlayNetworking.send(new UpdateSettingsPayload(3, warehouse.isQuickInteraction() ? 0 : 1));
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
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        
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

    @Inject(method = "render", at = @At("TAIL"))
    private void renderWarehouseContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());

        if (this.searchBox != null) {
            this.searchBox.setX(this.leftPos + WarehouseConstants.WAREHOUSE_X_OFFSET + WarehouseConstants.SEARCH_BOX_X_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.setY(this.topPos + WarehouseConstants.WAREHOUSE_Y_OFFSET + WarehouseConstants.SEARCH_BOX_Y_OFFSET + WarehouseConstants.SEARCH_BOX_INNER_OFFSET);
            this.searchBox.visible = !warehouse.isFolded();
            this.searchBox.active = !warehouse.isFolded();
        }

        if (warehouse.isFolded()) return; 
        
        int startX = this.leftPos + WarehouseConstants.SLOT_LOGIC_X;
        int startY = this.topPos + WarehouseConstants.SLOT_LOGIC_Y_BASE;

        for (int i = 0; i < warehouse.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW; i++) {
            long count = warehouse.getRealCount(i);
            if (count > 1) { 
                String countStr = WarehouseUtils.formatCount(count);
                int row = i / WarehouseConstants.SLOTS_PER_ROW;
                int col = i % WarehouseConstants.SLOTS_PER_ROW;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, WarehouseConstants.QUANTITY_TEXT_Z_OFFSET);
                float scale = WarehouseConstants.QUANTITY_TEXT_SCALE;
                int textX = startX + col * WarehouseConstants.SLOT_SIZE + WarehouseConstants.QUANTITY_TEXT_X_RELATIVE - (int)(this.font.width(countStr) * scale);
                int textY = startY + row * WarehouseConstants.SLOT_SIZE + WarehouseConstants.QUANTITY_TEXT_Y_RELATIVE;
                graphics.pose().translate(textX, textY, 0);
                graphics.pose().scale(scale, scale, 1.0f);
                graphics.drawString(this.font, countStr, 0, 0, WarehouseConstants.QUANTITY_TEXT_COLOR, true);
                graphics.pose().popPose();
            }
        }
    }
}
