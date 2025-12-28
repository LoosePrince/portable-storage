package com.portablestorage.component;

import com.portablestorage.logic.WarehouseManager;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.*;
import java.util.function.Consumer;

/**
 * 玩家仓库数据组件 (CCA Component)
 * 仅负责数据的持有、持久化 (NBT) 和基本的数据存取接口。
 * 复杂的业务逻辑（存取规则、流体转换等）应放在 WarehouseManager 中。
 */
public class PlayerWarehouse extends SnapshotParticipant<Map<FluidVariant, Long>> implements Container, Storage<FluidVariant> {
    private final List<WarehouseEntry> storage = new ArrayList<>();
    private final Map<FluidVariant, Long> fluidStorage = new LinkedHashMap<>();
    private int scrollOffset = 0;
    private int visibleRows = 6;
    private String searchText = "";
    private boolean isFolded = true;
    private int sortMode = 0; // 0: 数量, 1: 名称, 2: ID, 3: 更新时间
    private boolean isAscending = false;
    private boolean quickInteraction = true;
    private boolean smartCollapse = false;
    private boolean craftRefill = true;
    private boolean enabled = true;
    private List<WarehouseEntry> sortedCache = null;
    private final Consumer<PlayerWarehouse> onChanged;

    public PlayerWarehouse(UUID id, Consumer<PlayerWarehouse> onChanged) {
        this.onChanged = onChanged;
    }

    // --- 数据访问接口 (供逻辑层使用) ---

    public List<WarehouseEntry> getStorageList() {
        return storage;
    }

    public Map<FluidVariant, Long> getFluidStorageMap() {
        return fluidStorage;
    }

    public void markDirty() {
        this.sortedCache = null;
        if (onChanged != null) {
            onChanged.accept(this);
        }
    }

    /**
     * 仅使 UI 缓存失效，不触发 CCA 同步和持久化
     */
    public void markUIChanged() {
        this.sortedCache = null;
    }

    // --- Container 接口实现 (基础代理) ---

    @Override
    public int getContainerSize() {
        return visibleRows * 9;
            }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty() && fluidStorage.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slot + (scrollOffset * 9);
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            return sorted.get(actualIndex).getItemStack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return WarehouseManager.removeItem(this, slot, amount, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return WarehouseManager.removeItem(this, slot, Integer.MAX_VALUE, true);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // 仓库内容由自定义点击逻辑和 WarehouseManager 管理。
        // 忽略原版容器的 setItem 调用，防止在同步过程中数量意外累加。
    }

    @Override
    public void setChanged() {
        this.markDirty();
    }

    @Override
    public boolean stillValid(Player player) {
        return enabled;
    }

    @Override
    public void clearContent() {
        storage.clear();
        fluidStorage.clear();
        this.markDirty();
            }

    // --- Storage<FluidVariant> 接口实现 ---

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) return 0;
        updateSnapshots(transaction);
        long current = fluidStorage.getOrDefault(resource, 0L);
        fluidStorage.put(resource, current + maxAmount);
        this.markDirty();
        return maxAmount;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) return 0;
        long current = fluidStorage.getOrDefault(resource, 0L);
        if (current <= 0) return 0;
        updateSnapshots(transaction);
        long extracted = Math.min(current, maxAmount);
        if (current - extracted > 0) {
            fluidStorage.put(resource, current - extracted);
        } else {
            fluidStorage.remove(resource);
        }
        this.markDirty();
        return extracted;
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        return fluidStorage.entrySet().stream().map(e -> (StorageView<FluidVariant>) new StorageView<FluidVariant>() {
            @Override
            public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
                if (resource.equals(e.getKey())) {
                    return PlayerWarehouse.this.extract(resource, maxAmount, transaction);
                }
                return 0;
            }

            @Override
            public boolean isResourceBlank() { return e.getKey().isBlank(); }
            @Override
            public FluidVariant getResource() { return e.getKey(); }
            @Override
            public long getAmount() { return e.getValue(); }
            @Override
            public long getCapacity() { return Long.MAX_VALUE; }
        }).iterator();
    }

    @Override
    protected Map<FluidVariant, Long> createSnapshot() {
        return new LinkedHashMap<>(fluidStorage);
        }

    @Override
    protected void readSnapshot(Map<FluidVariant, Long> snapshot) {
        fluidStorage.clear();
        fluidStorage.putAll(snapshot);
    }

    // --- 状态控制 Getter/Setter ---

    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int offset) {
        int maxRows = (int) Math.ceil(getSortedEntries().size() / 9.0);
        int maxOffset = Math.max(0, maxRows - visibleRows);
        this.scrollOffset = Math.clamp(offset, 0, maxOffset);
        this.markUIChanged();
    }

    public int getVisibleRows() { return visibleRows; }
    public void setVisibleRows(int rows) {
        this.visibleRows = Math.clamp(rows, 1, 12);
        this.scrollOffset = 0;
        this.markDirty(); // 这个建议保留持久化，因为布局设置通常希望被记住
    }

    public boolean isFolded() { return isFolded; }
    public void setFolded(boolean folded) {
        if (!enabled && !folded) return;
        this.isFolded = folded;
        this.markDirty();
    }

    public int getSortMode() { return sortMode; }
    public void setSortMode(int mode) { this.sortMode = mode; this.markDirty(); }

    public boolean isAscending() { return isAscending; }
    public void setAscending(boolean ascending) { this.isAscending = ascending; this.markDirty(); }

    public boolean isQuickInteraction() { return quickInteraction; }
    public void setQuickInteraction(boolean quick) { this.quickInteraction = quick; this.markDirty(); }

    public boolean isSmartCollapse() { return smartCollapse; }
    public void setSmartCollapse(boolean smart) { this.smartCollapse = smart; this.markDirty(); }

    public boolean isCraftRefill() { return craftRefill; }
    public void setCraftRefill(boolean refill) { this.craftRefill = refill; this.markDirty(); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; this.markDirty(); }

    public String getSearchText() { return searchText; }
    public void setSearchText(String text) {
        this.searchText = text.toLowerCase();
        this.scrollOffset = 0;
        this.markUIChanged();
    }

    public long getRealCount(int slotIndex) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        return (actualIndex >= 0 && actualIndex < sorted.size()) ? sorted.get(actualIndex).getCount() : 0;
    }

    // --- 排序与缓存逻辑 ---

    public List<WarehouseEntry> getSortedEntries() {
        if (sortedCache == null) {
            List<WarehouseEntry> filtered = new ArrayList<>(storage);
            
            // 流体转换为展示物品
            for (Map.Entry<FluidVariant, Long> entry : fluidStorage.entrySet()) {
                net.minecraft.world.item.Item virtualItem = getVirtualItemForFluid(entry.getKey());
                if (virtualItem != null) {
                    long bucketCount = entry.getValue() / FluidConstants.BUCKET;
                    if (bucketCount > 0) {
                        filtered.add(new WarehouseEntry(new ItemStack(virtualItem), bucketCount));
                    }
                }
            }

            if (!searchText.isEmpty()) {
                String query = searchText.toLowerCase().trim();
                boolean exact = query.startsWith("!") && query.endsWith("!") && query.length() > 2;
                final String finalQuery = exact ? query.substring(1, query.length() - 1) : query;

                filtered = filtered.stream()
                        .filter(entry -> matchesQuery(entry, finalQuery, exact))
                        .toList();
            }

            if (smartCollapse && searchText.isEmpty()) {
                filtered = applySmartCollapse(filtered);
            }
            
            sortedCache = new ArrayList<>(filtered);
            applySorting(sortedCache);
        }
        return sortedCache;
    }

    private boolean matchesQuery(WarehouseEntry entry, String finalQuery, boolean exact) {
        ItemStack stack = entry.getItemStack();
        String name = stack.getHoverName().getString().toLowerCase();
        if (exact ? name.equals(finalQuery) : name.contains(finalQuery)) return true;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        if (exact ? id.equals(finalQuery) : id.contains(finalQuery)) return true;

        net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String lineText = line.getString().toLowerCase();
                if (exact ? lineText.equals(finalQuery) : lineText.contains(finalQuery)) return true;
            }
        }
        return false;
    }

    private List<WarehouseEntry> applySmartCollapse(List<WarehouseEntry> filtered) {
        Map<net.minecraft.world.item.Item, List<WarehouseEntry>> groups = new LinkedHashMap<>();
        for (WarehouseEntry entry : filtered) {
            groups.computeIfAbsent(entry.getItemStack().getItem(), k -> new ArrayList<>()).add(entry);
        }

        List<WarehouseEntry> collapsed = new ArrayList<>();
        for (Map.Entry<net.minecraft.world.item.Item, List<WarehouseEntry>> group : groups.entrySet()) {
            List<WarehouseEntry> entries = group.getValue();
            if (entries.size() > 1) {
                long totalCount = 0;
                long lastUpdated = 0;
                for (WarehouseEntry e : entries) {
                    totalCount += e.getCount();
                    lastUpdated = Math.max(lastUpdated, e.getLastUpdated());
                }
                
                ItemStack displayStack = new ItemStack(group.getKey());
                displayStack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                displayStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                    net.minecraft.world.item.component.CustomData.of(new net.minecraft.nbt.CompoundTag() {{
                        putBoolean(com.portablestorage.util.WarehouseConstants.SMART_COLLAPSE_TAG, true);
                    }}));
                
                collapsed.add(new WarehouseEntry(displayStack, totalCount));
            } else {
                collapsed.add(entries.get(0));
            }
        }
        return collapsed;
    }

    private void applySorting(List<WarehouseEntry> list) {
        Comparator<WarehouseEntry> comparator = switch (sortMode) {
            case 0 -> Comparator.comparingLong(WarehouseEntry::getCount);
            case 1 -> Comparator.comparing(e -> e.getItemStack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
            case 2 -> Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
            case 3 -> Comparator.comparingLong(WarehouseEntry::getLastUpdated);
            default -> (a, b) -> 0;
        };

        if (!isAscending) comparator = comparator.reversed();
        comparator = comparator.thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
        list.sort(comparator);
    }

    private net.minecraft.world.item.Item getVirtualItemForFluid(FluidVariant fluid) {
        if (fluid.isOf(Fluids.LAVA)) return com.portablestorage.item.ModItems.VIRTUAL_LAVA;
        if (fluid.isOf(Fluids.WATER)) return com.portablestorage.item.ModItems.VIRTUAL_WATER;
        return null;
    }

    // --- 持久化逻辑 ---

    public void readNbt(CompoundTag tag, HolderLookup.Provider registries) {
        storage.clear();
        ListTag list = tag.getList("storage", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            storage.add(WarehouseEntry.fromNbt(list.getCompound(i), registries));
        }

        fluidStorage.clear();
        if (tag.contains("fluids")) {
            ListTag fluidList = tag.getList("fluids", Tag.TAG_COMPOUND);
            for (int i = 0; i < fluidList.size(); i++) {
                CompoundTag fluidTag = fluidList.getCompound(i);
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(fluidTag.getString("fluid"));
                Fluid fluid = BuiltInRegistries.FLUID.get(id);
                
                net.minecraft.core.component.DataComponentPatch patch = net.minecraft.core.component.DataComponentPatch.EMPTY;
                if (fluidTag.contains("components")) {
                    patch = net.minecraft.core.component.DataComponentPatch.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, fluidTag.get("components"))
                        .getOrThrow();
                }
                
                FluidVariant variant = FluidVariant.of(fluid, patch);
                long amount = fluidTag.getLong("amount");
                fluidStorage.put(variant, amount);
            }
        }

        this.visibleRows = tag.contains("visibleRows") ? tag.getInt("visibleRows") : 6;
        this.isFolded = tag.contains("isFolded") ? tag.getBoolean("isFolded") : true;
        this.sortMode = tag.getInt("sortMode");
        this.isAscending = tag.getBoolean("isAscending");
        this.quickInteraction = tag.contains("quickInteraction") ? tag.getBoolean("quickInteraction") : true;
        this.smartCollapse = tag.getBoolean("smartCollapse");
        this.craftRefill = !tag.contains("craftRefill") || tag.getBoolean("craftRefill");
        this.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        this.markDirty();
    }

    public void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarehouseEntry entry : storage) list.add(entry.toNbt(registries));
        tag.put("storage", list);

        ListTag fluidList = new ListTag();
        for (Map.Entry<FluidVariant, Long> entry : fluidStorage.entrySet()) {
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString("fluid", BuiltInRegistries.FLUID.getKey(entry.getKey().getFluid()).toString());
            net.minecraft.core.component.DataComponentPatch patch = entry.getKey().getComponents();
            if (!patch.isEmpty()) {
                fluidTag.put("components", net.minecraft.core.component.DataComponentPatch.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, patch)
                    .getOrThrow());
            }
            fluidTag.putLong("amount", entry.getValue());
            fluidList.add(fluidTag);
        }
        tag.put("fluids", fluidList);

        tag.putInt("visibleRows", visibleRows);
        tag.putBoolean("isFolded", isFolded);
        tag.putInt("sortMode", sortMode);
        tag.putBoolean("isAscending", isAscending);
        tag.putBoolean("quickInteraction", quickInteraction);
        tag.putBoolean("smartCollapse", smartCollapse);
        tag.putBoolean("craftRefill", craftRefill);
        tag.putBoolean("enabled", enabled);
    }
}
