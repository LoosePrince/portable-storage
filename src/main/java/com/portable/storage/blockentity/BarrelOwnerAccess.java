package com.portable.storage.blockentity;

import java.util.UUID;

public interface BarrelOwnerAccess {
    UUID portableStorage$getOwnerUuid();
    String portableStorage$getOwnerName();
    void portableStorage$setOwner(UUID uuid, String name);
}


