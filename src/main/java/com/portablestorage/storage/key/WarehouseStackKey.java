package com.portablestorage.storage.key;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

public interface WarehouseStackKey {
    String TYPE_ITEM = "item";
    String TYPE_FLUID = "fluid";

    String typeId();

    CompoundTag toNbt(HolderLookup.Provider registries);

    static WarehouseStackKey fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        String type = tag.getString("type").orElse("");
        return switch (type) {
            case TYPE_ITEM -> ItemWarehouseKey.fromNbt(tag, registries);
            case TYPE_FLUID -> FluidWarehouseKey.fromNbt(tag);
            default -> throw new IllegalArgumentException("Unknown warehouse key type: " + type);
        };
    }

    static CompoundTag writeComponentsPatch(net.minecraft.core.component.DataComponentPatch patch) {
        if (patch == null || patch.isEmpty()) {
            return new CompoundTag();
        }
        return (CompoundTag) net.minecraft.core.component.DataComponentPatch.CODEC
                .encodeStart(NbtOps.INSTANCE, patch)
                .result()
                .orElse(new CompoundTag());
    }
}
