package com.portablestorage.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.PlayerWarehouse.WarehouseType;

class WarehouseMenuHandlerActivationTest {
    @Test
    void inactiveWarehouseIsNotInjectedIntoMenus() {
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.randomUUID(), ignored -> {
        });
        warehouse.setType(WarehouseType.NONE);
        warehouse.setEnabled(false);

        assertFalse(WarehouseMenuHandler.shouldInjectWarehouseSlots(warehouse));
    }

    @Test
    void enabledWarehouseCanBeInjectedIntoMenus() {
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.randomUUID(), ignored -> {
        });
        warehouse.setType(WarehouseType.BASE);
        warehouse.setEnabled(true);

        assertTrue(WarehouseMenuHandler.shouldInjectWarehouseSlots(warehouse));
    }
}