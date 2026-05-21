package com.portablestorage.component;

import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import java.util.UUID;
import java.util.Collection;

public interface WarehouseComponent extends AutoSyncedComponent {
    PlayerWarehouse getWarehouse(UUID uuid);
    void syncForPlayer(UUID uuid);
    Collection<PlayerWarehouse> getAllWarehouses();
    default void invalidateSharedGroupCaches() {
        for (PlayerWarehouse warehouse : getAllWarehouses()) {
            warehouse.invalidateSharedGroupCacheOnly();
        }
    }
}
