package com.portablestorage.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WarehouseMenuHandlerInteractionTest {
    @Test
    void rightClickTakesOneItemFromUpgradeSlot() {
        assertEquals(1, WarehouseMenuHandler.upgradeRemovalAmount(1, 64));
    }

    @Test
    void leftClickTakesTheAllowedUpgradeStack() {
        assertEquals(64, WarehouseMenuHandler.upgradeRemovalAmount(0, 64));
    }
}