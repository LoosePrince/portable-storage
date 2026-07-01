package com.portablestorage.component;

import java.util.UUID;

public interface WarehouseComponent extends WarehouseDirectory {
    @Override
    PlayerWarehouse getWarehouse(UUID uuid);

    void syncForPlayer(UUID uuid);
}
