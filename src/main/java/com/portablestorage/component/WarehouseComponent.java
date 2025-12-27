package com.portablestorage.component;

import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import java.util.UUID;

public interface WarehouseComponent extends AutoSyncedComponent {
    PlayerWarehouse getWarehouse(UUID uuid);
    void syncForPlayer(UUID uuid);
}