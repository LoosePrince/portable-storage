package com.portablestorage.client.handler;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TooltipHandler {

    public static boolean handleTooltip(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot hoveredSlot, int x, int y, Function<ItemStack, List<Component>> tooltipProvider) {
        if (hoveredSlot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            return true; // Cancel original tooltip
        }

        var player = Minecraft.getInstance().player;
        if (player == null || player.getAbilities().instabuild) return false;

        PlayerWarehouse wh = ModComponents.get(player).getWarehouse(player.getUUID());
        if (!wh.isEnabled() || wh.isFolded()) return false;

        if (hoveredSlot != null && hoveredSlot.container == wh) {
            int whStart = -1;
            for (int i = 0; i < screen.getMenu().slots.size(); i++) {
                if (screen.getMenu().slots.get(i).container == wh) {
                    whStart = i;
                    break;
                }
            }

            if (whStart != -1) {
                int whSlotEnd = whStart + (wh.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW);
                
                if (hoveredSlot.index >= whStart && hoveredSlot.index < whSlotEnd) {
                    long realCount = wh.getRealCount(hoveredSlot.index - whStart);
                    if (realCount > 1) {
                        List<Component> tooltip = new ArrayList<>(tooltipProvider.apply(hoveredSlot.getItem()));
                        tooltip.add(1, Component.translatable("gui.portablestorage.count", 
                            Component.literal(String.format("%,d", realCount)).withStyle(ChatFormatting.WHITE)
                        ).withStyle(ChatFormatting.YELLOW));
                        graphics.renderComponentTooltip(Minecraft.getInstance().font, tooltip, x, y);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

