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
    private int scrollOffset = 0;
    private int visibleRows = 6;
    private String searchText = "";
    private boolean isFolded = true;
    private int sortMode = 0; // 0: 数量, 1: 名称, 2: ID, 3: 更新时间
    private boolean isAscending = false;
    private boolean quickInteraction = true;
    private boolean enabled = true;
    private List<WarehouseEntry> sortedCache = null;
    private final Consumer<PlayerWarehouse> onChanged;

    public PlayerWarehouse(UUID id, Consumer<PlayerWarehouse> onChanged) {
        this.onChanged = onChanged;
    }

    public void setSearchText(String text) {
        this.searchText = text.toLowerCase();
        this.scrollOffset = 0;
        this.setChanged();
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
            List<WarehouseEntry> filtered = storage;
            if (!searchText.isEmpty()) {
                filtered = storage.stream()
                        .filter(entry -> entry.getItemStack().getHoverName().getString().toLowerCase().contains(searchText))
                        .toList();
            }
            sortedCache = new ArrayList<>(filtered);
            
            Comparator<WarehouseEntry> comparator = switch (sortMode) {
                case 0 -> Comparator.comparingLong(WarehouseEntry::getCount);
                case 1 -> Comparator.comparing(e -> e.getItemStack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
                case 2 -> Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
                case 3 -> Comparator.comparingLong(WarehouseEntry::getLastUpdated);
                default -> (a, b) -> 0;
            };

            if (!isAscending) comparator = comparator.reversed();
            
            // 稳定性排序：如果相等，按 ID 排序
            comparator = comparator.thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
            
            sortedCache.sort(comparator);
        }
        return sortedCache;
    }

    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int offset) {
        int maxRows = (int) Math.ceil(getSortedEntries().size() / 9.0);
        int maxOffset = Math.max(0, maxRows - visibleRows);
        this.scrollOffset = Math.clamp(offset, 0, maxOffset);
        this.setChanged();
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public void setVisibleRows(int rows) {
        this.visibleRows = Math.clamp(rows, 1, 12);
        this.scrollOffset = 0;
        this.setChanged();
    }

    public boolean isFolded() { return isFolded; }
    public void setFolded(boolean folded) { 
        if (!enabled && !folded) return; // 禁用时不允许展开
        this.isFolded = folded; 
        this.setChanged(); 
    }

    public int getSortMode() { return sortMode; }
    public void setSortMode(int mode) { this.sortMode = mode; this.setChanged(); }

    public boolean isAscending() { return isAscending; }
    public void setAscending(boolean ascending) { this.isAscending = ascending; this.setChanged(); }

    public boolean isQuickInteraction() { return quickInteraction; }
    public void setQuickInteraction(boolean quick) { this.quickInteraction = quick; this.setChanged(); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; this.setChanged(); }

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
        this.visibleRows = tag.contains("visibleRows") ? tag.getInt("visibleRows") : 6;
        this.isFolded = tag.contains("isFolded") ? tag.getBoolean("isFolded") : true;
        this.sortMode = tag.getInt("sortMode");
        this.isAscending = tag.getBoolean("isAscending");
        this.quickInteraction = tag.contains("quickInteraction") ? tag.getBoolean("quickInteraction") : true;
        this.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        this.searchText = tag.getString("searchText");
        this.sortedCache = null;
    }

    public void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarehouseEntry entry : storage) list.add(entry.toNbt(registries));
        tag.put("storage", list);
        tag.putInt("scrollOffset", scrollOffset);
        tag.putInt("visibleRows", visibleRows);
        tag.putBoolean("isFolded", isFolded);
        tag.putInt("sortMode", sortMode);
        tag.putBoolean("isAscending", isAscending);
        tag.putBoolean("quickInteraction", quickInteraction);
        tag.putBoolean("enabled", enabled);
        tag.putString("searchText", searchText);
    }

    @Override public int getContainerSize() { return visibleRows * 9; }
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

