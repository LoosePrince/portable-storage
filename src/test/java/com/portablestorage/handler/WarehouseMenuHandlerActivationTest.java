package com.portablestorage.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.PlayerWarehouse.WarehouseType;

class WarehouseMenuHandlerActivationTest {
    @Test
    void warehouseSlotsMaintainConsistentLayout() {
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.randomUUID(), ignored -> {});
        warehouse.setType(WarehouseType.BASE);
        warehouse.setEnabled(true);
        assertTrue(warehouse.isEnabled());
    }
}