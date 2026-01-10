package com.portablestorage.component;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import java.util.UUID;
import java.util.Collection;

public interface WarehouseComponent extends AutoSyncedComponent {
    PlayerWarehouse getWarehouse(UUID uuid);
    void syncForPlayer(UUID uuid);
    Collection<PlayerWarehouse> getAllWarehouses();
}
