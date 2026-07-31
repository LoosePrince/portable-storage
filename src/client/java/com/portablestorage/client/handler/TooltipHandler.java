
package com.portablestorage.client.handler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.item.ModItems;
import com.portablestorage.util.WarehouseConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TooltipHandler {

    public static boolean handleTooltip(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot hoveredSlot,
            int x, int y, Function<ItemStack, List<Component>> tooltipProvider) {
        if (hoveredSlot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            return true; // Cancel original tooltip
        }

        var player = Minecraft.getInstance().player;
        if (player == null || player.getAbilities().instabuild)
            return false;

        PlayerWarehouse wh = ModComponents.get(player).getWarehouse(player.getUUID());
        if (!wh.isEnabled() || wh.isFolded())
            return false;

        if (hoveredSlot != null && hoveredSlot.container instanceof PlayerWarehouse hoveredWarehouse) {
            int slotIndex = hoveredSlot.getContainerSlot();
            if (slotIndex >= 0 && slotIndex < hoveredWarehouse.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW) {
                long realCount = hoveredWarehouse.getRealCount(slotIndex);

                List<WarehouseEntry> entries = hoveredWarehouse.getSortedEntries();
                int actualIndex = slotIndex + (hoveredWarehouse.getScrollOffset() * 9);

                if (actualIndex >= 0 && actualIndex < entries.size()) {
                    WarehouseEntry entry = entries.get(actualIndex);
                    List<Component> tooltip = new ArrayList<>(tooltipProvider.apply(hoveredSlot.getItem()));

                    boolean hasCustomInfo = false;
                    int insertIndex = 1;

                    if (realCount > 1 && !hoveredSlot.getItem().is(ModItems.BOTTLED_EXPERIENCE)) {
                        tooltip.add(insertIndex, Component.translatable("gui.portablestorage.count",
                                Component.literal(String.format("%,d", realCount)).withStyle(ChatFormatting.WHITE))
                                .withStyle(ChatFormatting.YELLOW));
                        hasCustomInfo = true;
                        insertIndex++;
                    }

                    long lastUpdated = entry.getLastUpdated();
                    if (lastUpdated > 0) {
                        String timeText = formatDateTime(lastUpdated);
                        tooltip.add(insertIndex, Component.translatable("gui.portablestorage.update_time",
                                Component.literal(timeText).withStyle(ChatFormatting.WHITE))
                                .withStyle(ChatFormatting.GRAY));
                        hasCustomInfo = true;
                        insertIndex++;
                    }

                    if (entry.isPinned()) {
                        tooltip.add(insertIndex, Component.translatable("gui.portablestorage.button.unpin_hint"));
                        hasCustomInfo = true;
                    }

                    if (hasCustomInfo) {
                        graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                                tooltip.stream().map(Component::getVisualOrderText).toList(), x, y);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String formatDateTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        return dateTime.format(formatter);
    }
}