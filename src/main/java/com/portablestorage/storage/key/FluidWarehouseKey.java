package com.portablestorage.storage.key;

import java.util.Objects;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

public final class FluidWarehouseKey implements WarehouseStackKey {
    private final FluidVariant variant;

    public FluidWarehouseKey(FluidVariant variant) {
        this.variant = variant;
    }

    public FluidVariant variant() {
        return variant;
    }

    @Override
    public String typeId() {
        return TYPE_FLUID;
    }

    @Override
    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", TYPE_FLUID);
        tag.putString("fluid", BuiltInRegistries.FLUID.getKey(variant.getFluid()).toString());
        if (!variant.getComponents().isEmpty()) {
            tag.put("components", net.minecraft.core.component.DataComponentMap.CODEC
                    .encodeStart(NbtOps.INSTANCE, variant.getComponents())
                    .result()
                    .orElse(new CompoundTag()));
        }
        return tag;
    }

    public static FluidWarehouseKey fromNbt(CompoundTag tag) {
        String fluidId = tag.getString("fluid").orElse("");
        Identifier id = Identifier.tryParse(fluidId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid fluid id: " + fluidId);
        }
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        if (fluid == null) {
            throw new IllegalArgumentException("Unknown fluid id: " + fluidId);
        }

        net.minecraft.core.component.DataComponentPatch patch = net.minecraft.core.component.DataComponentPatch.EMPTY;
        if (tag.contains("components")) {
            patch = net.minecraft.core.component.DataComponentPatch.CODEC
                    .parse(NbtOps.INSTANCE, tag.get("components"))
                    .result()
                    .orElse(net.minecraft.core.component.DataComponentPatch.EMPTY);
        }
        return new FluidWarehouseKey(FluidVariant.of(fluid, patch));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FluidWarehouseKey other)) {
            return false;
        }
        return Objects.equals(this.variant, other.variant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variant);
    }
}
