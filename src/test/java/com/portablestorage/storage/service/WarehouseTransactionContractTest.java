package com.portablestorage.storage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.storage.key.WarehouseStackKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

class WarehouseTransactionContractTest {
    @Test
    void restoreUnifiedStorageSnapshotRevertsMutatedStorageWithoutExternalDirtyCallback() {
        AtomicInteger callbacks = new AtomicInteger();
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.fromString("00000000-0000-0000-0000-000000000401"),
                __ -> callbacks.incrementAndGet());
        TestWarehouseKey itemA = new TestWarehouseKey("item", "a");
        TestWarehouseKey itemB = new TestWarehouseKey("item", "b");

        Map<WarehouseStackKey, Long> baseline = mapOf(itemA, 10L);
        Map<WarehouseStackKey, Long> mutated = mapOf(itemA, 4L, itemB, 2L);

        warehouse.restoreUnifiedStorageSnapshot(baseline);
        callbacks.set(0);
        assertEquals(baseline, warehouse.unifiedStorageSnapshot());

        warehouse.restoreUnifiedStorageSnapshot(mutated);
        assertEquals(mutated, warehouse.unifiedStorageSnapshot());

        warehouse.restoreUnifiedStorageSnapshot(baseline);
        assertEquals(baseline, warehouse.unifiedStorageSnapshot());
        assertEquals(0, callbacks.get());
        assertEquals(0, warehouse.getDirtyCount());
    }

    @Test
    void sharedGroupRevisionSnapshotDetectsStorageRevisionChange() {
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.fromString("00000000-0000-0000-0000-000000000402"),
                __ -> {
                });
        TestWarehouseKey itemA = new TestWarehouseKey("item", "a");
        TestWarehouseKey itemB = new TestWarehouseKey("item", "b");

        warehouse.restoreUnifiedStorageSnapshot(mapOf(itemA, 10L));
        Map<UUID, Long> before = WarehouseService.sharedGroupRevisionSnapshot(warehouse);

        assertFalse(WarehouseService.hasSharedGroupChanged(warehouse, before));

        warehouse.restoreUnifiedStorageSnapshot(mapOf(itemA, 10L, itemB, 1L));

        assertTrue(WarehouseService.hasSharedGroupChanged(warehouse, before));
    }

    private static Map<WarehouseStackKey, Long> mapOf(WarehouseStackKey key, long amount) {
        Map<WarehouseStackKey, Long> map = new LinkedHashMap<>();
        map.put(key, amount);
        return map;
    }

    private static Map<WarehouseStackKey, Long> mapOf(WarehouseStackKey firstKey, long firstAmount,
            WarehouseStackKey secondKey, long secondAmount) {
        Map<WarehouseStackKey, Long> map = new LinkedHashMap<>();
        map.put(firstKey, firstAmount);
        map.put(secondKey, secondAmount);
        return map;
    }

    private record TestWarehouseKey(String typeId, String id) implements WarehouseStackKey {
        @Override
        public CompoundTag toNbt(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", typeId);
            tag.putString("id", id);
            return tag;
        }
    }
}