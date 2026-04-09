package com.portablestorage.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.material.Fluids;

@Disabled("Requires Minecraft bootstrap/runtime registries; keep as migration contract tests for game-test environment.")
class PlayerWarehouseMigrationTest {
    @Test
    void migrateV1FluidToV2KeepsAmount() {
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.randomUUID(), __ -> {
        });
        CompoundTag v1 = new CompoundTag();

        ListTag fluids = new ListTag();
        CompoundTag water = new CompoundTag();
        water.putString("fluid", "minecraft:water");
        water.putLong("amount", 3L * FluidConstants.BUCKET);
        fluids.add(water);
        v1.put("fluids", fluids);

        warehouse.readNbt(v1, null);

        assertEquals(3L * FluidConstants.BUCKET, warehouse.getFluidAmount(FluidVariant.of(Fluids.WATER)));

        CompoundTag out = new CompoundTag();
        warehouse.writeNbt(out, null);
        assertEquals(2, out.getInt("warehouse_schema_version").orElse(1));
        assertTrue(out.contains("unified_storage"));
    }

    @Test
    void malformedV1FluidEntryDoesNotThrow() {
        PlayerWarehouse warehouse = new PlayerWarehouse(UUID.randomUUID(), __ -> {
        });
        CompoundTag v1 = new CompoundTag();

        ListTag fluids = new ListTag();
        CompoundTag broken = new CompoundTag();
        broken.putString("fluid", "not-a-valid-fluid");
        broken.putLong("amount", 1000L);
        fluids.add(broken);
        v1.put("fluids", fluids);

        assertDoesNotThrow(() -> warehouse.readNbt(v1, null));
        assertTrue(warehouse.getFluidStorageMap().isEmpty());
    }
}
