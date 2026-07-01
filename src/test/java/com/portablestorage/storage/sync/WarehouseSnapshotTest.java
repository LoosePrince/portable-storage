package com.portablestorage.storage.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.core.RegistryAccess;

class WarehouseSnapshotTest {
    @Test
    void snapshotCanRehydrateClientWarehouseFromCopiedNbtPayload() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000501");
        PlayerWarehouse source = new PlayerWarehouse(owner, __ -> {
        });
        source.setOwnerName("Alice");
        source.setEnabled(true);
        source.setType(PlayerWarehouse.WarehouseType.FULL);
        source.setFolded(false);
        source.setVisibleRows(6);
        source.setExperience(88L);

        WarehouseSnapshot snapshot = WarehouseSnapshot.fromWarehouse(source, RegistryAccess.EMPTY);
        PlayerWarehouse clientWarehouse = snapshot.toClientWarehouse(RegistryAccess.EMPTY);

        assertEquals(owner, snapshot.ownerUuid());
        assertEquals(owner, clientWarehouse.getOwnerUuid());
        assertEquals("Alice", clientWarehouse.getOwnerName());
        assertTrue(clientWarehouse.isEnabled());
        assertFalse(clientWarehouse.isFolded());
        assertEquals(6, clientWarehouse.getVisibleRows());
        assertEquals(88L, clientWarehouse.getExperience());

        snapshot.warehouseData().putString("ownerName", "MutatedAfterHydration");
        assertEquals("Alice", clientWarehouse.getOwnerName());
    }
}