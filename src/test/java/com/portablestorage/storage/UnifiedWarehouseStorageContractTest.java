package com.portablestorage.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.portablestorage.storage.core.UnifiedWarehouseStorage;
import com.portablestorage.storage.key.WarehouseStackKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

class UnifiedWarehouseStorageContractTest {
    @Test
    void insertAndExtractKeepVisibleAmountsAndRevisionContract() {
        AtomicInteger changed = new AtomicInteger();
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> changed.incrementAndGet());
        WarehouseStackKey itemA = new TestWarehouseKey("item", "a");

        assertEquals(10, storage.insert(itemA, 10, false));
        assertEquals(10, storage.getAmount(itemA));
        assertTrue(storage.isDirty());
        assertEquals(1, storage.getLastModified());
        assertEquals(1, changed.get());

        assertEquals(4, storage.extract(itemA, 4, false));
        assertEquals(6, storage.getAmount(itemA));
        assertEquals(2, storage.getLastModified());
        assertEquals(2, changed.get());
    }

    @Test
    void simulationDoesNotMutateVisibleState() {
        AtomicInteger changed = new AtomicInteger();
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> changed.incrementAndGet());
        WarehouseStackKey itemA = new TestWarehouseKey("item", "a");

        assertEquals(10, storage.insert(itemA, 10, true));
        assertEquals(0, storage.getAmount(itemA));
        assertFalse(storage.isDirty());
        assertEquals(0, storage.getLastModified());
        assertEquals(0, changed.get());

        storage.insert(itemA, 5, false);
        assertEquals(5, storage.extract(itemA, 99, true));
        assertEquals(5, storage.getAmount(itemA));
        assertEquals(1, storage.getLastModified());
        assertEquals(1, changed.get());
    }

    @Test
    void incrementalIndexesMatchSnapshotState() {
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> {
        });
        WarehouseStackKey itemA = new TestWarehouseKey("item", "a");
        WarehouseStackKey itemB = new TestWarehouseKey("item", "b");
        WarehouseStackKey fluidA = new TestWarehouseKey("fluid", "a");

        storage.insert(itemA, 1, false);
        storage.insert(itemB, 2, false);
        storage.insert(fluidA, 3, false);
        storage.extract(itemA, 1, false);

        assertEquals(Map.of(itemB, 2L, fluidA, 3L), storage.snapshot());
        assertFalse(storage.getSlotIndexSnapshot().containsKey(itemA));
        assertTrue(storage.getSlotIndexSnapshot().containsKey(itemB));
        assertTrue(storage.getSlotIndexSnapshot().containsKey(fluidA));
        assertEquals(1, storage.getTypeBucketsSnapshot().get("item").size());
        assertEquals(1, storage.getTypeBucketsSnapshot().get("fluid").size());
    }

    @Test
    void replaceAllPreservesExistingSlotForRemainingKeys() {
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> {
        });
        WarehouseStackKey itemA = new TestWarehouseKey("item", "a");
        WarehouseStackKey itemB = new TestWarehouseKey("item", "b");
        WarehouseStackKey itemC = new TestWarehouseKey("item", "c");

        storage.insert(itemA, 1, false);
        storage.insert(itemB, 2, false);
        int oldSlotB = storage.getSlotIndexSnapshot().get(itemB);

        Map<WarehouseStackKey, Long> next = new LinkedHashMap<>();
        next.put(itemB, 20L);
        next.put(itemC, 30L);
        storage.replaceAll(next);

        assertEquals(20, storage.getAmount(itemB));
        assertEquals(30, storage.getAmount(itemC));
        assertEquals(oldSlotB, storage.getSlotIndexSnapshot().get(itemB));
        assertFalse(storage.getSlotIndexSnapshot().containsKey(itemA));
    }

    @Test
    void typeCountFollowsIncrementalIndexUpdates() {
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> {
        });
        WarehouseStackKey itemA = new TestWarehouseKey("item", "a");
        WarehouseStackKey itemB = new TestWarehouseKey("item", "b");
        WarehouseStackKey fluidA = new TestWarehouseKey("fluid", "a");

        storage.insert(itemA, 1, false);
        storage.insert(itemB, 2, false);
        storage.insert(fluidA, 3, false);

        assertEquals(2, storage.getTypeCount("item"));
        assertEquals(1, storage.getTypeCount("fluid"));
        assertEquals(0, storage.getTypeCount("missing"));

        storage.extract(itemA, 1, false);

        assertEquals(1, storage.getTypeCount("item"));
        assertEquals(1, storage.getTypeCount("fluid"));
    }

    @Test
    void toolEntriesUseSeparateBucketAndLogicalSlots() {
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> {
        });
        WarehouseStackKey itemStone = new TestWarehouseKey(WarehouseStackKey.TYPE_ITEM, "stone");
        WarehouseStackKey toolStone = new TestWarehouseKey(WarehouseStackKey.TYPE_TOOL, "slot0:stone");

        storage.insert(itemStone, 64, false);
        storage.insert(toolStone, 1, false);

        assertEquals(1, storage.getTypeCount(WarehouseStackKey.TYPE_ITEM));
        assertEquals(1, storage.getTypeCount(WarehouseStackKey.TYPE_TOOL));
        assertEquals(2, storage.getSlotIndexSnapshot().size());
        assertEquals(64, storage.getAmount(itemStone));
        assertEquals(1, storage.getAmount(toolStone));
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