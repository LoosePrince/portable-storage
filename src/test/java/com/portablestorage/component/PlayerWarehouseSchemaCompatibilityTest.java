package com.portablestorage.component;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.material.Fluids;

class PlayerWarehouseSchemaCompatibilityTest {
    @Test
    void v2NbtRoundTripKeepsStateFieldsWithoutDirtyCallbackOnLoad() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID forbidden = UUID.fromString("00000000-0000-0000-0000-000000000302");
        PlayerWarehouse source = new PlayerWarehouse(owner, __ -> {
        });
        source.setOwnerName("Alice");
        source.setEnabled(true);
        source.setType(PlayerWarehouse.WarehouseType.FULL);
        source.setFolded(false);
        source.setVisibleRows(9);
        source.setSortMode(2);
        source.setAscending(true);
        source.setQuickInteraction(false);
        source.setSmartCollapse(true);
        source.setCraftRefill(false);
        source.setExperience(123L);
        source.setHopperFilters(List.of("minecraft:stone", "minecraft:dirt"), false);
        source.setFoodFilters(List.of("minecraft:apple"), true);
        source.setForbidden(forbidden, true);
        source.setRiftPlotX(11);
        source.setRiftPlotZ(12);
        source.setRiftInitialized(true);

        CompoundTag tag = new CompoundTag();
        source.writeNbt(tag, NbtOps.INSTANCE);

        AtomicInteger callbacks = new AtomicInteger();
        PlayerWarehouse target = new PlayerWarehouse(owner, __ -> callbacks.incrementAndGet());
        target.loadFromNbt(tag.copy(), NbtOps.INSTANCE);

        assertEquals(0, callbacks.get());
        assertEquals(0, target.getDirtyCount());
        assertEquals(2, target.getLoadedSchemaVersion());
        assertTrue(target.isLoadedFromUnifiedStorage());
        assertEquals("Alice", target.getOwnerName());
        assertEquals(PlayerWarehouse.WarehouseType.FULL, target.getType());
        assertTrue(target.isEnabled());
        assertFalse(target.isFolded());
        assertEquals(9, target.getVisibleRows());
        assertEquals(2, target.getSortMode());
        assertTrue(target.isAscending());
        assertFalse(target.isQuickInteraction());
        assertTrue(target.isSmartCollapse());
        assertFalse(target.isCraftRefill());
        assertEquals(123L, target.getExperience());
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), target.getHopperFilters());
        assertFalse(target.isHopperFilterBlacklist());
        assertEquals(List.of("minecraft:apple"), target.getFoodFilters());
        assertTrue(target.isFoodFilterBlacklist());
        assertTrue(target.isForbidden(forbidden));
        assertEquals(11, target.getRiftPlotX());
        assertEquals(12, target.getRiftPlotZ());
        assertTrue(target.isRiftInitialized());
    }

    @Disabled("需要 Minecraft 运行期注册表；保留为旧内层 schema 兼容契约，不代表 CCA 迁移路径。")
    @Test
    void legacyV1FluidPayloadCanStillBeReadByInnerWarehouseSchema() {
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
        warehouse.writeNbt(out, (net.minecraft.core.HolderLookup.Provider) null);
        assertEquals(2, out.getInt("warehouse_schema_version").orElse(1));
        assertTrue(out.contains("unified_storage"));
    }

    @Disabled("需要 Minecraft 运行期注册表；保留为旧内层 schema 容错契约，不代表 CCA 迁移路径。")
    @Test
    void malformedLegacyFluidEntryDoesNotThrowWhenRuntimeRegistriesAreAvailable() {
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