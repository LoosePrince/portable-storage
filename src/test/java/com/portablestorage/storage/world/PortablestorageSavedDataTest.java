package com.portablestorage.storage.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;

class PortablestorageSavedDataTest {
    @Test
    void writesSavedDataEnvelopeWithPlayerWarehouseV2Payload() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000101");
        PortablestorageSavedData data = new PortablestorageSavedData();
        PlayerWarehouse warehouse = data.getOrCreateWarehouse(owner, "Alice");
        warehouse.setEnabled(true);
        warehouse.setType(PlayerWarehouse.WarehouseType.FULL);
        warehouse.setVisibleRows(8);
        warehouse.setExperience(42);

        CompoundTag root = data.writeRoot(NbtOps.INSTANCE);

        assertEquals(PortablestorageSavedData.SCHEMA_VERSION,
                root.getInt("portablestorage_schema_version").orElseThrow());
        ListTag warehouses = root.getList("warehouses").orElseThrow();
        assertEquals(1, warehouses.size());

        CompoundTag entry = warehouses.getCompound(0).orElseThrow();
        assertEquals(owner.toString(), entry.getString("id").orElseThrow());

        CompoundTag payload = entry.getCompound("data").orElseThrow();
        assertEquals(2, payload.getInt("warehouse_schema_version").orElseThrow());
        assertEquals(8, payload.getInt("visibleRows").orElseThrow());
        assertEquals(42L, payload.getLong("experience").orElseThrow());
        assertEquals("Alice", payload.getString("ownerName").orElseThrow());
    }

    @Test
    void codecReadsValidWarehousesAndIgnoresMalformedUuidEntries() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000202");
        CompoundTag root = new CompoundTag();
        root.putInt("portablestorage_schema_version", PortablestorageSavedData.SCHEMA_VERSION);

        ListTag warehouses = new ListTag();
        warehouses.add(entry(first.toString(), warehousePayload("First", 5)));
        warehouses.add(entry("not-a-uuid", warehousePayload("Broken", 6)));
        warehouses.add(entry(second.toString(), warehousePayload("Second", 7)));
        root.put("warehouses", warehouses);

        PortablestorageSavedData decoded = PortablestorageSavedData.CODEC.decode(NbtOps.INSTANCE, root)
                .result()
                .orElseThrow()
                .getFirst();

        assertEquals(2, decoded.getAllWarehouses().size());
        assertNotNull(decoded.getWarehouse(first));
        assertNull(decoded.getWarehouse(UUID.fromString("00000000-0000-0000-0000-000000000299")));
        assertEquals("First", decoded.getWarehouse(first).getOwnerName());
        assertEquals(5, decoded.getWarehouse(first).getVisibleRows());
        assertEquals("Second", decoded.getWarehouse(second).getOwnerName());
        assertEquals(7, decoded.getWarehouse(second).getVisibleRows());
        assertEquals(0, decoded.getWarehouse(first).getDirtyCount());
    }

    @Test
    void savedDataTypeUsesNamespacedFileIdContract() {
        assertEquals("portablestorage", PortablestorageSavedData.FILE_ID);
        assertEquals("portablestorage", PortablestorageSavedData.TYPE.id().getNamespace());
        assertEquals("portablestorage", PortablestorageSavedData.TYPE.id().getPath());
    }

    private static CompoundTag entry(String id, CompoundTag payload) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", id);
        entry.put("data", payload);
        return entry;
    }

    private static CompoundTag warehousePayload(String ownerName, int visibleRows) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("warehouse_schema_version", 2);
        tag.put("unified_storage", new ListTag());
        tag.putString("ownerName", ownerName);
        tag.putInt("visibleRows", visibleRows);
        tag.putBoolean("enabled", true);
        tag.putInt("activationType", PlayerWarehouse.WarehouseType.FULL.ordinal());
        return tag;
    }
}