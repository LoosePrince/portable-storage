package com.portablestorage.mixin.client;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow protected Slot hoveredSlot;

    @Shadow protected abstract List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack);

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        int whStart = WarehouseConstants.getWarehouseSlotStart();
        if (this.hoveredSlot != null && this.hoveredSlot.index >= whStart) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            
            PlayerWarehouse wh = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
            int whSlotEnd = whStart + (wh.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW);
            
            if (this.hoveredSlot.index >= whStart && this.hoveredSlot.index < whSlotEnd) {
                long realCount = wh.getRealCount(this.hoveredSlot.index - whStart);
                if (realCount > 1) {
                    // 获取原版 Tooltip 列表
                    List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(this.hoveredSlot.getItem()));
                    // 插入数量信息
                    tooltip.add(1, Component.translatable("gui.portablestorage.count", String.format("%,d", realCount)).withStyle(ChatFormatting.GRAY));
                    // 手动渲染并拦截原版调用，防止重叠
                    graphics.renderComponentTooltip(Minecraft.getInstance().font, tooltip, x, y);
                    ci.cancel();
                }
            }
        }
    }
}
