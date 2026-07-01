package com.portablestorage.storage.sync;

import java.util.UUID;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public record WarehouseSnapshot(UUID ownerUuid, CompoundTag warehouseData) {
    public static WarehouseSnapshot fromWarehouse(PlayerWarehouse warehouse, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (warehouse != null) {
            warehouse.writeNbt(tag, registries);
        }
        UUID ownerUuid = warehouse == null ? null : warehouse.getOwnerUuid();
        return new WarehouseSnapshot(ownerUuid, tag);
    }

    public PlayerWarehouse toClientWarehouse(HolderLookup.Provider registries) {
        PlayerWarehouse warehouse = new PlayerWarehouse(ownerUuid, __ -> {
        });
        warehouse.loadFromNbt(warehouseData.copy(), registries);
        return warehouse;
    }
}