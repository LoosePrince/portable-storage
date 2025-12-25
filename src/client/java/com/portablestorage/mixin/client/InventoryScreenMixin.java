package com.portablestorage.mixin.client;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.ChangeRowsPayload;
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
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        int rows = warehouse.getVisibleRows();
        
        int yOffset = ModConfig.offsetInventory ? 10 + rows * 10 : 0; // 动态偏移，保证界面美观
        if (yOffset > 0) {
            this.topPos -= yOffset; 
        }
        this.imageHeight = 166 + 4 + 27 + rows * 18; // 166 + 间隔 + 搜索框区域 + 槽位区域

        // 兼容性修复：将所有控件（包括其他模组添加的）同步上移
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                if (yOffset > 0) {
                    widget.setY(widget.getY() - yOffset);
                }

                // 移除配方书按钮的判定逻辑（需匹配偏移后的坐标）
                if (ModConfig.hideRecipeBook) {
                    if (widget.getY() == (this.height / 2 - 22 - yOffset)) {
                        widget.visible = false;
                        widget.active = false;
                    }
                }
            }
        }

        // 仓库宽度 192，居中于背包 (176)
        int x = this.leftPos - 8 + 16; 
        int y = this.topPos + 166 + 4 + 6;
        this.searchBox = new EditBox(this.font, x, y, 140, 12, Component.literal("")); // 缩短宽度给按钮腾位置
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
        var player = Minecraft.getInstance().player;
        if (player == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        int rows = warehouse.getVisibleRows();
        
        int warehouseX = this.leftPos - 8;
        int warehouseY = this.topPos + 170;
        int warehouseHeight = 27 + rows * 18;
        if (mouseX >= warehouseX && mouseX < warehouseX + 192 && mouseY >= warehouseY && mouseY < warehouseY + warehouseHeight) {
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
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        int rows = warehouse.getVisibleRows();
        
        int warehouseHeight = 27 + rows * 18;
        this.imageHeight = 166 + 4 + warehouseHeight;
        int x = this.leftPos - 8; // 居中对齐
        int y = this.topPos + 166 + 4;
        drawNinePatch(graphics, WAREHOUSE_GUI_TEXTURE, x, y, 192, warehouseHeight, 10);
        
        // 搜索框背景与描边 (内部描边：左上暗，右下亮)
        int sbX = x + 16;
        int sbY = y + 5;
        int sbW = 141; // 缩短以适应按钮
        int sbH = 12;
        graphics.fill(sbX, sbY, sbX + sbW, sbY + sbH, 0xFF222222); // 底色
        graphics.fill(sbX, sbY, sbX + sbW, sbY + 1, 0xFF111111); // 上边 (暗)
        graphics.fill(sbX, sbY, sbX + 1, sbY + sbH, 0xFF111111); // 左边 (暗)
        graphics.fill(sbX, sbY + sbH - 1, sbX + sbW, sbY + sbH, 0xFF555555); // 下边 (亮)
        graphics.fill(sbX + sbW - 1, sbY, sbX + sbW, sbY + sbH, 0xFF555555); // 右边 (亮)

        // 绘制 +/- 按钮
        renderPlusMinusButtons(graphics, x + 160, y + 5, mouseX, mouseY);
        
        int slotStartX = x + 15; 
        int slotStartY = y + 20;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blit(WAREHOUSE_SLOT_TEXTURE, slotStartX + col * 18, slotStartY + row * 18, 0, 0, 18, 18, 18, 18);
            }
        }

        // 绘制滚动条
        int scrollbarX = x + 182; 
        int scrollbarY = y + 23;
        int scrollbarHeight = rows * 18 - 4;
        int scrollbarWidth = 4;
        
        if (scrollbarHeight > 0) {
            // 背景
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFF333333);
            
            // 滑块
            int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / 9.0);
            int visibleRows = rows;
            int thumbHeight = (totalRows <= visibleRows) ? scrollbarHeight : Math.max(10, (int) (scrollbarHeight * ((float) visibleRows / totalRows)));
            int maxOffset = Math.max(0, totalRows - visibleRows);
            int thumbY = scrollbarY + (maxOffset == 0 ? 0 : (warehouse.getScrollOffset() * (scrollbarHeight - thumbHeight) / maxOffset));
            
            int thumbColor = (isDraggingScrollbar || (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth && mouseY >= thumbY && mouseY <= thumbY + thumbHeight)) ? 0xFFAAAAAA : 0xFF888888;
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
            
            graphics.fill(scrollbarX - 1, thumbY - 1, scrollbarX + scrollbarWidth, thumbY, 0xFFBBBBBB); 
            graphics.fill(scrollbarX - 1, thumbY, scrollbarX, thumbY + thumbHeight, 0xFFBBBBBB); 
            graphics.fill(scrollbarX, thumbY + thumbHeight, scrollbarX + scrollbarWidth + 1, thumbY + thumbHeight + 1, 0xFF444444); 
            graphics.fill(scrollbarX + scrollbarWidth, thumbY - 1, scrollbarX + scrollbarWidth + 1, thumbY + thumbHeight, 0xFF444444); 
        }
    }

    @Unique
    private void renderPlusMinusButtons(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // 减号按钮
        renderTinyButton(graphics, x, y, "-", mouseX, mouseY);
        // 加号按钮
        renderTinyButton(graphics, x + 14, y, "+", mouseX, mouseY);
    }

    @Unique
    private void renderTinyButton(GuiGraphics graphics, int x, int y, String text, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
        int color = hovered ? 0xFFAAAAAA : 0xFF888888;
        
        // 背景
        graphics.fill(x, y, x + 12, y + 12, color);
        // 外部描边
        graphics.fill(x - 1, y - 1, x + 12, y, 0xFFBBBBBB); 
        graphics.fill(x - 1, y, x, y + 12, 0xFFBBBBBB); 
        graphics.fill(x, y + 12, x + 13, y + 13, 0xFF444444); 
        graphics.fill(x + 12, y - 1, x + 13, y + 12, 0xFF444444); 

        // 文字居中
        int textX = x + 6 - this.font.width(text) / 2;
        int textY = y + 2;
        graphics.drawString(this.font, text, textX, textY, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (shouldShowWarehouse()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
                int rows = warehouse.getVisibleRows();
                int x = this.leftPos - 8;
                int y = this.topPos + 166 + 4;
                
                // 减号按钮
                if (mouseX >= x + 160 && mouseX < x + 172 && mouseY >= y + 5 && mouseY < y + 17) {
                    ClientPlayNetworking.send(new ChangeRowsPayload(-1));
                    this.minecraft.setScreen(new InventoryScreen(player)); // 立即重新初始化界面
                    return true;
                }
                // 加号按钮
                if (mouseX >= x + 174 && mouseX < x + 186 && mouseY >= y + 5 && mouseY < y + 17) {
                    ClientPlayNetworking.send(new ChangeRowsPayload(1));
                    this.minecraft.setScreen(new InventoryScreen(player)); // 立即重新初始化界面
                    return true;
                }

                // 滚动条
                int sx = x + 182;
                int sy = y + 23;
                int sh = rows * 18 - 4;
                if (mouseX >= sx && mouseX <= sx + 4 && mouseY >= sy && mouseY <= sy + sh) {
                    this.isDraggingScrollbar = true;
                    this.updateScrollFromMouse(mouseY);
                    return true;
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
        
        int scrollbarY = this.topPos + 166 + 4 + 23;
        int scrollbarHeight = warehouse.getVisibleRows() * 18 - 4;
        
        int totalRows = (int) Math.ceil(warehouse.getSortedEntries().size() / 9.0);
        int visibleRows = warehouse.getVisibleRows();
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
        
        // 实时同步搜索框位置，防止打开配方书时界面位移导致脱节
        if (this.searchBox != null) {
            this.searchBox.setX(this.leftPos - 8 + 16);
            this.searchBox.setY(this.topPos + 166 + 4 + 6);
        }

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        int startX = this.leftPos + 8; // 统一使用 +8 对齐
        int startY = this.topPos + 166 + 4 + 20;

        for (int i = 0; i < warehouse.getVisibleRows() * 9; i++) {
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

        if (this.hoveredSlot != null && this.hoveredSlot.index >= 46) {
            var player2 = Minecraft.getInstance().player;
            if (player2 != null) {
                PlayerWarehouse warehouse2 = ModComponents.WAREHOUSE.get(player2.level()).getWarehouse(player2.getUUID());
                int warehouseSlotStart = 46;
                int warehouseSlotEnd = warehouseSlotStart + (warehouse2.getVisibleRows() * 9);
                
                if (this.hoveredSlot.index < warehouseSlotEnd) {
                    long realCount = warehouse2.getRealCount(this.hoveredSlot.index - 46);
                    if (realCount > 1) { // 只要大于1就显示详细数量
                        List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
                        tooltip.add(1, Component.translatable("gui.portablestorage.count", String.format("%,d", realCount))
                                .withStyle(ChatFormatting.GRAY));
                        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                    }
                }
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
