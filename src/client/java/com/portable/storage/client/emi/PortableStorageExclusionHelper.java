package com.portable.storage.client.emi;

import java.util.function.Consumer;

import com.portable.storage.client.ClientConfig;

import dev.emi.emi.api.widget.Bounds;

/**
 * Helper to register EMI exclusion areas for Portable Storage UI overlays.
 */
public final class PortableStorageExclusionHelper {
    private PortableStorageExclusionHelper() {}

    public static void addAreasForScreen(Consumer<Bounds> consumer,
                                         int x, int y, int backgroundWidth, int backgroundHeight,
                                         boolean storageOnTop,
                                         boolean includeCollapseButton,
                                         boolean includeSearchPosSetting,
                                         boolean includeSwitchButton) {
        // Layout params aligned with StorageUIComponent
        final int cols = 9;
        final int slotSize = 18;
        final int slotSpacing = 0;
        final int gridLeft = x + 8;

        int visibleRows = ClientConfig.getInstance().maxVisibleRows;
        if (visibleRows < 2) visibleRows = 2;
        int storageWidth = cols * (slotSize + slotSpacing);
        int storageHeight = visibleRows * (slotSize + slotSpacing);

        int storageTop = storageOnTop
            ? (y - storageHeight - 6)
            : (y + backgroundHeight + 6);

        // 1) Storage grid area
        consumer.accept(new Bounds(gridLeft, storageTop, storageWidth, storageHeight));

        // 2) Upgrade columns (right/base and left/extended)
        int upgradeLeft = x - 24; // base column (right)
        int upgradeWidth = 18;
        consumer.accept(new Bounds(upgradeLeft, storageTop, upgradeWidth, storageHeight));
        int extendedLeft = upgradeLeft - (slotSize + slotSpacing + 2); // extended column (left)
        consumer.accept(new Bounds(extendedLeft, storageTop, upgradeWidth, storageHeight));

        // 3) Settings panel area (width computed like StorageUIComponent.calculatePanelWidth)
        int panelLeft = x + backgroundWidth + 8;
        int panelTop = storageTop - 6;
        int panelBottom = storageTop + storageHeight + 8;

        int settingCount = 0;
        if (includeCollapseButton) settingCount++;
        settingCount += 4; // sort mode, sort order, craft refill, auto deposit
        settingCount += 1; // smart collapse
        if (includeSearchPosSetting) settingCount += 1; // search position
        settingCount += 1; // storage position
        if (includeSwitchButton) settingCount += 1; // switch to vanilla

        int iconSize = 16;
        int iconSpacing = 15;
        int columnWidth = iconSize + 2;
        int currentColumn = 0;
        int currentRow = 0;
        for (int i = 0; i < settingCount; i++) {
            int nextIconY = panelTop + currentRow * iconSpacing;
            int nextIconBottom = nextIconY + iconSize;
            if (nextIconBottom > panelBottom) {
                currentRow = 0;
                currentColumn++;
            } else {
                currentRow++;
            }
        }
        int columns = currentColumn + 1;
        int padding = 4;
        int settingsWidth = columns * columnWidth + padding + 8; // +8 to match visual padding
        consumer.accept(new Bounds(panelLeft, panelTop, settingsWidth, panelBottom - panelTop));
    }
}
