package com.portablestorage.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerWarehouse implements Container {
    private final List<WarehouseEntry> storage = new ArrayList<>();
    private final UUID id;
    private int scrollOffset = 0;
    private List<WarehouseEntry> sortedCache = null;
    private final Consumer<PlayerWarehouse> onChanged;

    public PlayerWarehouse(UUID id, Consumer<PlayerWarehouse> onChanged) {
        this.id = id;
        this.onChanged = onChanged;
    }

    public void addItem(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (WarehouseEntry entry : storage) {
            if (entry.matches(stack)) {
                entry.add(stack.getCount());
                stack.setCount(0);
                this.setChanged();
                return;
            }
        }
        storage.add(new WarehouseEntry(stack.copy(), stack.getCount()));
        stack.setCount(0);
        this.setChanged();
    }

    public ItemStack removeItem(int slot, int amount) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slot + (scrollOffset * 9);
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            WarehouseEntry entry = sorted.get(actualIndex);
            long toRemove = Math.min(amount, entry.getCount());
            ItemStack result = entry.getItemStack().copyWithCount((int)toRemove);
            entry.subtract(toRemove);
            if (entry.getCount() <= 0) storage.remove(entry);
            this.setChanged();
            return result;
        }
        return ItemStack.EMPTY;
    }

    public List<WarehouseEntry> getSortedEntries() {
        if (sortedCache == null) {
            sortedCache = new ArrayList<>(storage);
            sortedCache.sort((a, b) -> {
                int res = Long.compare(b.getCount(), a.getCount());
                if (res == 0) res = Long.compare(b.getLastUpdated(), a.getLastUpdated());
                if (res == 0) res = BuiltInRegistries.ITEM.getKey(a.getItemStack().getItem())
                        .compareTo(BuiltInRegistries.ITEM.getKey(b.getItemStack().getItem()));
                return res;
            });
        }
        return sortedCache;
    }

    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int offset) {
        int maxRows = (int) Math.ceil(storage.size() / 9.0);
        int maxOffset = Math.max(0, maxRows - 6);
        this.scrollOffset = Math.clamp(offset, 0, maxOffset);
        this.setChanged();
    }

    public long getRealCount(int slotIndex) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        return (actualIndex >= 0 && actualIndex < sorted.size()) ? sorted.get(actualIndex).getCount() : 0;
    }

    public void readNbt(CompoundTag tag, HolderLookup.Provider registries) {
        storage.clear();
        ListTag list = tag.getList("storage", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            storage.add(WarehouseEntry.fromNbt(list.getCompound(i), registries));
        }
        this.scrollOffset = tag.getInt("scrollOffset");
        this.sortedCache = null;
    }

    public void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarehouseEntry entry : storage) list.add(entry.toNbt(registries));
        tag.put("storage", list);
        tag.putInt("scrollOffset", scrollOffset);
    }

    @Override public int getContainerSize() { return 54; }
    @Override public boolean isEmpty() { return storage.isEmpty(); }
    @Override public ItemStack getItem(int slot) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slot + (scrollOffset * 9);
        return (actualIndex >= 0 && actualIndex < sorted.size()) ? sorted.get(actualIndex).getItemStack().copyWithCount(1) : ItemStack.EMPTY;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
    
    // 仓库内容由 CCA 同步和我们的自定义点击逻辑管理，
    // 忽略原版容器的 setItem 同步调用，防止数量在客户端意外叠加。
    @Override public void setItem(int slot, ItemStack stack) { }
    
    @Override public void setChanged() { this.sortedCache = null; if (onChanged != null) onChanged.accept(this); }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() { storage.clear(); this.setChanged(); }
}

