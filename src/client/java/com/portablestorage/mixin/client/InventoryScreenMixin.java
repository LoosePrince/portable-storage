package com.portablestorage.mixin.client;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.ScrollPayload;
import com.portablestorage.network.SearchPayload;
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
        this.topPos -= 70; 
        this.imageHeight = 166 + 4 + 135; 
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.getY() == (this.height / 2 - 22)) {
                    widget.visible = false;
                    widget.active = false;
                }
            }
        }

        // 仓库宽度 192，居中于背包 (176)
        int x = this.leftPos - 8 + 16; // 相对仓库左侧偏移 16px，即对齐背包 leftPos + 8
        int y = this.topPos + 166 + 4 + 6;
        this.searchBox = new EditBox(this.font, x, y, 160, 12, Component.literal(""));
        this.searchBox.setResponder(text -> {
            ClientPlayNetworking.send(new SearchPayload(text));
        });
        this.searchBox.setEditable(true);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.portablestorage.search").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!shouldShowWarehouse()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int warehouseX = this.leftPos - 8;
        int warehouseY = this.topPos + 170;
        if (mouseX >= warehouseX && mouseX < warehouseX + 192 && mouseY >= warehouseY && mouseY < warehouseY + 135) {
            ClientPlayNetworking.send(new ScrollPayload((int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Inject(method = "renderBg", at = @At("HEAD"))
    protected void onRenderBgHead(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        this.imageHeight = 166;
    }

    @Inject(method = "renderBg", at = @At("RETURN"))
    protected void onRenderBgReturn(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        this.imageHeight = 166 + 4 + 135;
        int x = this.leftPos - 8; // 居中对齐
        int y = this.topPos + 166 + 4;
        drawNinePatch(graphics, WAREHOUSE_GUI_TEXTURE, x, y, 192, 135, 10);
        
        // 搜索框背景与描边 (内部描边：左上暗，右下亮)
        int sbX = x + 16;
        int sbY = y + 5;
        int sbW = 161;
        int sbH = 12;
        graphics.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0xFF222222); // 底色
        graphics.fill(sbX, sbY, sbX + sbW, sbY + 1, 0xFF111111); // 上边 (暗)
        graphics.fill(sbX, sbY, sbX + 1, sbY + sbH, 0xFF111111); // 左边 (暗)
        graphics.fill(sbX, sbY + sbH - 1, sbX + sbW, sbY + sbH, 0xFF555555); // 下边 (亮)
        graphics.fill(sbX + sbW - 1, sbY, sbX + sbW, sbY + sbH, 0xFF555555); // 右边 (亮)
        
        int slotStartX = x + 15; // 对应 leftPos + 8
        int slotStartY = y + 20;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blit(WAREHOUSE_SLOT_TEXTURE, slotStartX + col * 18, slotStartY + row * 18, 0, 0, 18, 18, 18, 18);
            }
        }

        // 绘制滚动条
        var player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
            int scrollbarX = x + 182; // 移动到背景内部右侧
            int scrollbarY = y + 23;
            int scrollbarHeight = 104;
            int scrollbarWidth = 4;
            
            // 背景
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFF333333);
            
            // 滑块
            int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / 9.0);
            int visibleRows = 6;
            int thumbHeight = (totalRows <= visibleRows) ? scrollbarHeight : Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalRows)));
            int maxOffset = Math.max(0, totalRows - visibleRows);
            int thumbY = scrollbarY + (maxOffset == 0 ? 0 : (warehouse.getScrollOffset() * (scrollbarHeight - thumbHeight) / maxOffset));
            
            // 滑块描边 (外部描边：左上亮，右下灰)
            int thumbColor = (isDraggingScrollbar || (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth && mouseY >= thumbY && mouseY <= thumbY + thumbHeight)) ? 0xFFAAAAAA : 0xFF888888;
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
            
            // 描边线 (1px)
            graphics.fill(scrollbarX - 1, thumbY - 1, scrollbarX + scrollbarWidth, thumbY, 0xFFBBBBBB); // 上 (亮)
            graphics.fill(scrollbarX - 1, thumbY, scrollbarX, thumbY + thumbHeight, 0xFFBBBBBB); // 左 (亮)
            graphics.fill(scrollbarX, thumbY + thumbHeight, scrollbarX + scrollbarWidth + 1, thumbY + thumbHeight + 1, 0xFF444444); // 下 (灰)
            graphics.fill(scrollbarX + scrollbarWidth, thumbY - 1, scrollbarX + scrollbarWidth + 1, thumbY + thumbHeight, 0xFF444444); // 右 (灰)
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (shouldShowWarehouse()) {
            int x = this.leftPos - 8 + 182;
            int y = this.topPos + 166 + 4 + 23;
            if (mouseX >= x && mouseX <= x + 4 && mouseY >= y && mouseY <= y + 108) {
                this.isDraggingScrollbar = true;
                this.updateScrollFromMouse(mouseY);
                return true;
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
        
        int scrollbarY = this.topPos + 166 + 4 + 23;
        int scrollbarHeight = 108;
        
        int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / 9.0);
        int visibleRows = 6;
        int maxOffset = Math.max(0, totalRows - visibleRows);
        
        if (maxOffset > 0) {
            int thumbHeight = Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalRows)));
            double relativeY = Math.clamp(mouseY - scrollbarY - thumbHeight / 2.0, 0, scrollbarHeight - thumbHeight);
            int newOffset = (int) Math.round((relativeY * maxOffset) / (scrollbarHeight - thumbHeight));
            
            if (newOffset != warehouse.getScrollOffset()) {
                ClientPlayNetworking.send(new ScrollPayload(warehouse.getScrollOffset() - newOffset));
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == 256) { // ESC
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // 拦截所有非控制键，防止触发游戏快捷键（如 'E'）
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
        int startX = this.leftPos + 8; // 统一使用 +8 对齐
        int startY = this.topPos + 166 + 4 + 20;

        for (int i = 0; i < 54; i++) {
            long count = warehouse.getRealCount(i);
            if (count > 1) { // 大于1才显示数量
                String countStr = formatCount(count);
                int row = i / 9;
                int col = i % 9;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 300); // 300刚刚好
                float scale = 0.8f;
                int textX = startX + col * 18 + 17 - (int)(this.font.width(countStr) * scale);
                int textY = startY + row * 18 + 12;
                graphics.pose().translate(textX, textY, 0);
                graphics.pose().scale(scale, scale, 1.0f);
                graphics.drawString(this.font, countStr, 0, 0, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
        }

        if (this.hoveredSlot != null && this.hoveredSlot.index >= 46 && this.hoveredSlot.index < 100) {
            long realCount = warehouse.getRealCount(this.hoveredSlot.index - 46);
            if (realCount > 1) { // 只要大于1就显示详细数量
                List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
                tooltip.add(1, Component.translatable("gui.portablestorage.count", String.format("%,d", realCount))
                        .withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }
    }

    @Unique
    private String formatCount(long count) {
        if (count >= 1_000_000_000) return String.format("%.1fG", Math.floor(count / 100_000_000.0) / 10.0);
        if (count >= 1_000_000) return String.format("%.1fM", Math.floor(count / 100_000.0) / 10.0);
        if (count >= 1_000) return String.format("%.1fk", Math.floor(count / 100.0) / 10.0);
        return String.valueOf(count);
    }

    @Unique
    private void drawNinePatch(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height, int cornerSize) {
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
}
