package com.portablestorage.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class WarehouseEntry {
    private final ItemStack itemStack; // 用于存储 Item 和 NBT
    private long count;
    private long lastUpdated;

    public WarehouseEntry(ItemStack stack, long count) {
        this.itemStack = stack.copyWithCount(1);
        this.count = count;
        this.lastUpdated = System.currentTimeMillis();
    }

    public ItemStack getItemStack() { return itemStack; }
    public long getCount() { return count; }
    public long getLastUpdated() { return lastUpdated; }

    public void add(long amount) {
        this.count += amount;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void subtract(long amount) {
        this.count -= amount;
        this.lastUpdated = System.currentTimeMillis();
    }

    // 唯一标识逻辑：Item + NBT 相同则视为同一种物品
    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(this.itemStack, stack);
    }

    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", itemStack.save(registries));
        tag.putLong("count", count);
        tag.putLong("lastUpdated", lastUpdated);
        return tag;
    }

    public static WarehouseEntry fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack stack = ItemStack.parseOptional(registries, tag.getCompound("item"));
        long count = tag.getLong("count");
        WarehouseEntry entry = new WarehouseEntry(stack, count);
        entry.lastUpdated = tag.getLong("lastUpdated");
        return entry;
    }
}

