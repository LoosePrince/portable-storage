package com.portablestorage.storage.key;

import java.util.Objects;

import com.portablestorage.component.WarehouseEntry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class ItemWarehouseKey implements WarehouseStackKey {
    private final ItemStack template;
    private final DataComponentPatch componentsPatch;

    public ItemWarehouseKey(ItemStack stack) {
        this.template = stack.copyWithCount(1);
        this.componentsPatch = this.template.getComponentsPatch();
    }

    public ItemStack toStack() {
        return template.copyWithCount(1);
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(this.template, stack);
    }

    public DataComponentPatch getComponentsPatch() {
        return componentsPatch;
    }

    @Override
    public String typeId() {
        return TYPE_ITEM;
    }

    @Override
    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", TYPE_ITEM);
        tag.put("stack", WarehouseEntry.itemToNbt(template, registries));
        return tag;
    }

    public static ItemWarehouseKey fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag stackTag = tag.getCompound("stack").orElse(new CompoundTag());
        ItemStack stack = WarehouseEntry.itemFromNbt(stackTag, registries);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Invalid item key payload");
        }
        return new ItemWarehouseKey(stack);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ItemWarehouseKey other)) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(this.template, other.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template.getItem(), componentsPatch);
    }
}
