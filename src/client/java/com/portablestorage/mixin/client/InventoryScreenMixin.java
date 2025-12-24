package com.portablestorage.mixin.client;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.ScrollPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
        this.topPos -= 60; 
        this.imageHeight = 166 + 4 + 120; 
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.getY() == (this.height / 2 - 22)) {
                    widget.visible = false;
                    widget.active = false;
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!shouldShowWarehouse()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int warehouseX = this.leftPos;
        int warehouseY = this.topPos + 170;
        if (mouseX >= warehouseX && mouseX < warehouseX + 176 && mouseY >= warehouseY && mouseY < warehouseY + 120) {
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
        this.imageHeight = 166 + 4 + 120;
        int x = this.leftPos;
        int y = this.topPos + 166 + 4;
        drawNinePatch(graphics, WAREHOUSE_GUI_TEXTURE, x, y, 176, 120, 10);
        int slotStartX = x + 7;
        int slotStartY = y + 7;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blit(WAREHOUSE_SLOT_TEXTURE, slotStartX + col * 18, slotStartY + row * 18, 0, 0, 18, 18, 18, 18);
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderWarehouseContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!shouldShowWarehouse()) return;
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        // 改为从世界组件获取特定玩家的仓库
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        int startX = this.leftPos + 7;
        int startY = this.topPos + 166 + 4 + 7;

        for (int i = 0; i < 54; i++) {
            long count = warehouse.getRealCount(i);
            if (count > 1) {
                String countStr = formatCount(count);
                int row = i / 9;
                int col = i % 9;
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 300); 
                int textX = startX + col * 18 + 19 - this.font.width(countStr);
                int textY = startY + row * 18 + 10;
                graphics.drawString(this.font, countStr, textX, textY, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
        }

        if (this.hoveredSlot != null && this.hoveredSlot.index >= 46 && this.hoveredSlot.index < 100) {
            long realCount = warehouse.getRealCount(this.hoveredSlot.index - 46);
            if (realCount > 99) {
                List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
                tooltip.add(1, Component.translatable("gui.portablestorage.count", String.format("%, d", realCount))
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
