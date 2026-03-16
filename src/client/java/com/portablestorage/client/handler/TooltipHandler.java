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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TooltipHandler {

    public static boolean handleTooltip(AbstractContainerScreen<?> screen, GuiGraphics graphics, Slot hoveredSlot,
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
                    int slotIndex = hoveredSlot.index - whStart;
                    long realCount = wh.getRealCount(slotIndex);

                    // 获取对应的仓库条目以获取更新时间
                    List<WarehouseEntry> entries = wh.getSortedEntries();
                    int actualIndex = slotIndex + (wh.getScrollOffset() * 9);

                    if (actualIndex >= 0 && actualIndex < entries.size()) {
                        WarehouseEntry entry = entries.get(actualIndex);
                        List<Component> tooltip = new ArrayList<>(tooltipProvider.apply(hoveredSlot.getItem()));

                        boolean hasCustomInfo = false;
                        int insertIndex = 1;

                        // 添加数量信息（数量大于1时显示），但“瓶装经验”有独立数量显示，不再叠加这行
                        if (realCount > 1 && !hoveredSlot.getItem().is(ModItems.BOTTLED_EXPERIENCE)) {
                            tooltip.add(insertIndex, Component.translatable("gui.portablestorage.count",
                                    Component.literal(String.format("%,d", realCount)).withStyle(ChatFormatting.WHITE))
                                    .withStyle(ChatFormatting.YELLOW));
                            hasCustomInfo = true;
                            insertIndex++;
                        }

                        // 添加更新时间信息（总是显示）
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
                            List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components = tooltip
                                    .stream()
                                    .map(component -> net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                                            .create(component.getVisualOrderText()))
                                    .collect(java.util.stream.Collectors.toList());
                            graphics.renderTooltip(Minecraft.getInstance().font, components, x, y,
                                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                                    null);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 格式化日期时间
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的日期时间字符串（格式：xxxx/xx/xx xx:xx）
     */
    private static String formatDateTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        return dateTime.format(formatter);
    }
}
