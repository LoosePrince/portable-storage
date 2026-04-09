package com.portablestorage.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.portablestorage.storage.core.UnifiedWarehouseStorage;
import com.portablestorage.storage.key.ItemWarehouseKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Disabled("Requires Minecraft bootstrap/runtime registries.")
class StorageBenchmarkTest {
    @Test
    void benchmark5kInsertExtractAnd10kSortRead() {
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> {
        });
        long start = System.nanoTime();
        for (int i = 0; i < 5000; i++) {
            storage.insert(new ItemWarehouseKey(new ItemStack(Items.STONE, 1)), i + 1L, false);
        }
        for (int i = 0; i < 5000; i++) {
            storage.extract(new ItemWarehouseKey(new ItemStack(Items.STONE, 1)), 1L, false);
        }
        long phaseA = System.nanoTime() - start;

        for (int i = 0; i < 10000; i++) {
            storage.insert(new ItemWarehouseKey(new ItemStack(Items.COBBLESTONE, 1)), 1L, false);
        }
        long phaseBStart = System.nanoTime();
        int count = storage.snapshot().entrySet().stream()
                .sorted((a, b) -> Long.compare(a.getValue(), b.getValue()))
                .limit(200)
                .toList()
                .size();
        long phaseB = System.nanoTime() - phaseBStart;

        System.out.println("Benchmark phaseA(ns)=" + phaseA + " phaseB(ns)=" + phaseB + " count=" + count);
        assertTrue(count > 0);
    }
}
