package com.portablestorage.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.portablestorage.storage.core.UnifiedWarehouseStorage;
import com.portablestorage.storage.key.FluidWarehouseKey;
import com.portablestorage.storage.key.ItemWarehouseKey;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

@Disabled("Requires Minecraft bootstrap/runtime registries.")
class UnifiedWarehouseStorageTest {
    @Test
    void shouldTrackRevisionAndIndexes() {
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> {
        });
        ItemWarehouseKey itemA = new ItemWarehouseKey(new ItemStack(Items.STONE));
        FluidWarehouseKey fluidW = new FluidWarehouseKey(FluidVariant.of(Fluids.WATER));

        long rev0 = storage.getLastModified();
        storage.insert(itemA, 10, false);
        storage.insert(fluidW, 1000, false);

        assertTrue(storage.getLastModified() > rev0);
        assertEquals(2, storage.getSlotIndexSnapshot().size());
        assertEquals(1, storage.getTypeBucketsSnapshot().get("item").size());
        assertEquals(1, storage.getTypeBucketsSnapshot().get("fluid").size());

        storage.extract(itemA, 5, false);
        assertEquals(5, storage.getAmount(itemA));
    }

    @Test
    void shouldSetDirtyOnMutation() {
        UnifiedWarehouseStorage storage = new UnifiedWarehouseStorage(__ -> {
        });
        ItemWarehouseKey itemA = new ItemWarehouseKey(new ItemStack(Items.STONE));
        assertFalse(storage.isDirty());
        storage.insert(itemA, 1, false);
        assertTrue(storage.isDirty());
        storage.clearDirty();
        assertFalse(storage.isDirty());
    }
}
