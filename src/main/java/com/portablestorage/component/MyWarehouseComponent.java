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

public class MyWarehouseComponent implements WarehouseComponent, Container {
    private final List<WarehouseEntry> storage = new ArrayList<>();
    private final Object provider;
    private int scrollOffset = 0;
    
    // 增加排序缓存以提高性能
    private List<WarehouseEntry> sortedCache = null;

    public MyWarehouseComponent(Object provider) {
        this.provider = provider;
    }

    private boolean isClient() {
        return provider instanceof Player p && p.level().isClientSide();
    }

    @Override
    public void addItem(ItemStack stack) {
        if (stack.isEmpty() || isClient()) return;
        
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

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (isClient()) return ItemStack.EMPTY;

        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slot + (scrollOffset * 9);
        
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            WarehouseEntry entry = sorted.get(actualIndex);
            long toRemove = Math.min(amount, entry.getCount());
            ItemStack result = entry.getItemStack().copyWithCount((int)toRemove);
            entry.subtract(toRemove);
            
            if (entry.getCount() <= 0) {
                storage.remove(entry);
            }
            
            this.setChanged();
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<WarehouseEntry> getEntries() {
        return storage;
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

    @Override
    public int getScrollOffset() { return scrollOffset; }

    @Override
    public void setScrollOffset(int offset) {
        int maxRows = (int) Math.ceil(storage.size() / 9.0);
        int maxOffset = Math.max(0, maxRows - 6);
        this.scrollOffset = Math.clamp(offset, 0, maxOffset);
        this.setChanged();
    }

    @Override
    public ItemStack getViewSlot(int slotIndex) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            // 关键：返回给容器接口的数量始终为 1
            // 这样网络发包时永远不会超过 127，杜绝崩溃
            return sorted.get(actualIndex).getItemStack().copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public long getRealCount(int slotIndex) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            return sorted.get(actualIndex).getCount();
        }
        return 0;
    }

    // CCA Methods
    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        storage.clear();
        ListTag list = tag.getList("storage", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            storage.add(WarehouseEntry.fromNbt(list.getCompound(i), registries));
        }
        this.scrollOffset = tag.getInt("scrollOffset");
        this.sortedCache = null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarehouseEntry entry : storage) {
            list.add(entry.toNbt(registries));
        }
        tag.put("storage", list);
        tag.putInt("scrollOffset", scrollOffset);
    }

    // Container Interface
    @Override public int getContainerSize() { return 54; }
    @Override public boolean isEmpty() { return storage.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return getViewSlot(slot); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
    @Override public void setItem(int slot, ItemStack stack) { if (!isClient()) addItem(stack); }
    
    @Override public void setChanged() { 
        this.sortedCache = null; // 数据变化，清空缓存
        if (provider instanceof Player p) ModComponents.WAREHOUSE.sync(p); 
    }
    
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() { if (!isClient()) { storage.clear(); this.setChanged(); } }
}
