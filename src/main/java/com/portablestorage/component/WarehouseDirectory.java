package com.portablestorage.component;

import java.util.Collection;
import java.util.UUID;

public interface WarehouseDirectory {
    PlayerWarehouse getWarehouse(UUID uuid);

    Collection<PlayerWarehouse> getAllWarehouses();

    default void invalidateSharedGroupCaches() {
        for (PlayerWarehouse warehouse : getAllWarehouses()) {
            warehouse.invalidateSharedGroupCacheOnly();
        }
    }
}