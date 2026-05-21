package com.portablestorage.storage.key;

import java.util.Objects;

import com.portablestorage.component.WarehouseEntry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class ToolWarehouseKey implements WarehouseStackKey {
    public static final int SLOT_COUNT = 9;

    private final int slot;
    private final ItemStack template;
    private final DataComponentPatch componentsPatch;

    public ToolWarehouseKey(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid tool warehouse slot: " + slot);
        }
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Tool warehouse key requires a non-empty stack");
        }
        this.slot = slot;
        this.template = stack.copyWithCount(1);
        this.componentsPatch = this.template.getComponentsPatch();
    }

    public int slot() {
        return slot;
    }

    public ItemStack toStack() {
        return template.copyWithCount(1);
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(this.template, stack);
    }

    @Override
    public String typeId() {
        return TYPE_TOOL;
    }

    @Override
    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", TYPE_TOOL);
        tag.putInt("tool_slot", slot);
        tag.put("stack", WarehouseEntry.itemToNbt(template, registries));
        return tag;
    }

    public static ToolWarehouseKey fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        int slot = tag.getInt("tool_slot").orElse(-1);
        CompoundTag stackTag = tag.getCompound("stack").orElse(new CompoundTag());
        ItemStack stack = WarehouseEntry.itemFromNbt(stackTag, registries);
        if (slot < 0 || slot >= SLOT_COUNT || stack.isEmpty()) {
            throw new IllegalArgumentException("Invalid tool warehouse key payload");
        }
        return new ToolWarehouseKey(slot, stack);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ToolWarehouseKey other)) {
            return false;
        }
        return slot == other.slot && ItemStack.isSameItemSameComponents(this.template, other.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, template.getItem(), componentsPatch);
    }
}