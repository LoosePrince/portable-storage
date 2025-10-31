package com.portable.storage.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.blockentity.BarrelOwnerAccess;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.nbt.NbtCompound;
// 1.20.1: writeNbt/readNbt 不含 WrapperLookup 参数

/**
 * Mixin to BarrelBlockEntity to add owner tracking for bound barrels.
 */
@Mixin(BarrelBlockEntity.class)
public abstract class BarrelBlockEntityMixin implements BarrelOwnerAccess {
    @Unique private UUID portableStorage$ownerUuid;
    @Unique private String portableStorage$ownerName;

    @Override
    public UUID portableStorage$getOwnerUuid() {
        return portableStorage$ownerUuid;
    }

    @Override
    public String portableStorage$getOwnerName() {
        return portableStorage$ownerName;
    }

    @Override
    public void portableStorage$setOwner(UUID uuid, String name) {
        this.portableStorage$ownerUuid = uuid;
        this.portableStorage$ownerName = name;
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void portableStorage$writeOwner(NbtCompound nbt, CallbackInfo ci) {
        if (portableStorage$ownerUuid != null) {
            nbt.putUuid("ps_owner_uuid", portableStorage$ownerUuid);
        }
        if (portableStorage$ownerName != null) {
            nbt.putString("ps_owner_name", portableStorage$ownerName);
        }
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void portableStorage$readOwner(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.containsUuid("ps_owner_uuid")) {
            portableStorage$ownerUuid = nbt.getUuid("ps_owner_uuid");
        }
        if (nbt.contains("ps_owner_name")) {
            portableStorage$ownerName = nbt.getString("ps_owner_name");
        }
    }
}
