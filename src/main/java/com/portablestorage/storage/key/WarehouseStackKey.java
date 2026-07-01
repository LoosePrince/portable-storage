package com.portablestorage.storage.key;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

public interface WarehouseStackKey {
    String TYPE_ITEM = "item";
    String TYPE_FLUID = "fluid";
    String TYPE_TOOL = "tool";

    String typeId();

    CompoundTag toNbt(HolderLookup.Provider registries);

    default CompoundTag toNbt(DynamicOps<Tag> ops) {
        return toNbt(net.minecraft.core.RegistryAccess.EMPTY);
    }

    static WarehouseStackKey fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        return fromNbt(tag, registries.createSerializationContext(NbtOps.INSTANCE));
    }

    static WarehouseStackKey fromNbt(CompoundTag tag, DynamicOps<Tag> ops) {
        String type = tag.getString("type").orElse("");
        return switch (type) {
            case TYPE_ITEM -> ItemWarehouseKey.fromNbt(tag, ops);
            case TYPE_FLUID -> FluidWarehouseKey.fromNbt(tag);
            case TYPE_TOOL -> ToolWarehouseKey.fromNbt(tag, ops);
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
