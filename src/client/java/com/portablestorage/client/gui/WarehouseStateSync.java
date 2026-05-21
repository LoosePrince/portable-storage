package com.portablestorage.client.gui;

import java.util.Optional;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.C2SUpdateWarehouseStatePayload;
import com.portablestorage.util.WarehouseSetting;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Keeps warehouse UI state sync payload construction in one place without changing the wire format.
 */
public final class WarehouseStateSync {
    private WarehouseStateSync() {
    }

    public static void sendSearchText(String text) {
        send(Optional.empty(), Optional.of(text), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static void sendSetting(WarehouseSetting setting, int value) {
        send(Optional.empty(), Optional.empty(), Optional.of(setting.ordinal()), Optional.of(value), Optional.empty(),
                Optional.empty());
    }

    public static void applySetting(PlayerWarehouse warehouse, WarehouseSetting setting, int value) {
        switch (setting) {
            case SORT_MODE -> warehouse.setSortMode(value);
            case SORT_ORDER -> warehouse.setAscending(value == 1);
            case QUICK_INTERACTION -> warehouse.setQuickInteraction(value == 1);
            case SMART_COLLAPSE -> warehouse.setSmartCollapse(value == 1);
            case CRAFT_REFILL -> warehouse.setCraftRefill(value == 1);
            case FOLD -> warehouse.setFolded(value == 1);
        }
    }

    public static int nextSidebarSettingValue(PlayerWarehouse warehouse, WarehouseSetting setting) {
        return switch (setting) {
            case SORT_MODE -> (warehouse.getSortMode() + 1) % 4;
            case SORT_ORDER -> warehouse.isAscending() ? 0 : 1;
            case QUICK_INTERACTION -> warehouse.isQuickInteraction() ? 0 : 1;
            case SMART_COLLAPSE -> warehouse.isSmartCollapse() ? 0 : 1;
            case CRAFT_REFILL -> warehouse.isCraftRefill() ? 0 : 1;
            case FOLD -> warehouse.isFolded() ? 0 : 1;
        };
    }

    public static void sendRowsDelta(int delta) {
        send(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta), Optional.empty());
    }

    public static void sendScrollDelta(int delta) {
        send(Optional.of(delta), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static void sendUpgradeScrollDelta(int delta) {
        send(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(delta));
    }

    private static void send(Optional<Integer> scrollDelta, Optional<String> searchText, Optional<Integer> settingId,
            Optional<Integer> settingValue, Optional<Integer> rowsDelta, Optional<Integer> upgradeScrollDelta) {
        ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(scrollDelta, searchText, settingId, settingValue,
                rowsDelta, upgradeScrollDelta));
    }
}