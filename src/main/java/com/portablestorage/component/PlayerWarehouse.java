package com.portablestorage.component;

import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.upgrade.UpgradeRegistry;
import com.portablestorage.upgrade.UpgradeType;
import com.portablestorage.util.WarehouseConstants;
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
import net.minecraft.resources.ResourceLocation;
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

    // 升级系统数据
    private final Map<ResourceLocation, ItemStack> upgradeStorage = new LinkedHashMap<>();
    private int upgradeScrollOffset = 0;

    /**
     * 专用升级容器，支持滚动窗口映射
     */
    public final net.minecraft.world.Container upgradeContainer = new net.minecraft.world.Container() {
        @Override
        public int getContainerSize() {
            return WarehouseConstants.MAX_ROWS;
        }

        @Override
        public boolean isEmpty() {
            return upgradeStorage.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            List<UpgradeType> all = UpgradeRegistry.getAllUpgrades();
            int actualIndex = slot + upgradeScrollOffset;
            if (actualIndex >= 0 && actualIndex < all.size()) {
                return upgradeStorage.getOrDefault(all.get(actualIndex).getId(), ItemStack.EMPTY);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                List<UpgradeType> all = UpgradeRegistry.getAllUpgrades();
                int actualIndex = slot + upgradeScrollOffset;
                setUpgrade(all.get(actualIndex).getId(), ItemStack.EMPTY);
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            List<UpgradeType> all = UpgradeRegistry.getAllUpgrades();
            int actualIndex = slot + upgradeScrollOffset;
            if (actualIndex >= 0 && actualIndex < all.size()) {
                setUpgrade(all.get(actualIndex).getId(), stack);
            }
        }

        @Override
        public void setChanged() {
            markDirty();
        }

        @Override
        public boolean stillValid(Player player) {
            return enabled;
        }

        @Override
        public void clearContent() {
            upgradeStorage.clear();
            markDirty();
        }
    };

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

    // 多级缓存
    private List<WarehouseEntry> baseCache = null;      // 原始项 + 流体
    private List<WarehouseEntry> filteredCache = null;  // 搜索过滤后
    private List<WarehouseEntry> collapsedCache = null; // 智能折叠后
    private List<WarehouseEntry> sortedCache = null;    // 最终排序后

    private final Consumer<PlayerWarehouse> onChanged;

    public PlayerWarehouse(UUID id, Consumer<PlayerWarehouse> onChanged) {
        this.onChanged = onChanged;
    }

    // --- 升级系统接口 ---

    public ItemStack getUpgrade(ResourceLocation id) {
        return upgradeStorage.getOrDefault(id, ItemStack.EMPTY);
    }

    public void setUpgrade(ResourceLocation id, ItemStack stack) {
        ItemStack old = upgradeStorage.getOrDefault(id, ItemStack.EMPTY);
        UpgradeType type = UpgradeRegistry.get(id);

        // 如果是替换操作，先卸载旧的
        if (type != null && !old.isEmpty()) {
            type.onUninstall(this, old);
        }

        if (stack.isEmpty()) {
            upgradeStorage.remove(id);
        } else {
            upgradeStorage.put(id, stack.copyWithCount(1));
            // 安装新的
            if (type != null) {
                type.onInstall(this, stack);
            }
        }
        this.markDirty();
    }

    public Map<ResourceLocation, ItemStack> getUpgradeStorage() {
        return upgradeStorage;
    }

    public int getUpgradeScrollOffset() {
        return upgradeScrollOffset;
    }

    public void setUpgradeScrollOffset(int offset) {
        int maxOffset = Math.max(0, UpgradeRegistry.getUpgradeCount() - visibleRows);
        this.upgradeScrollOffset = Math.clamp(offset, 0, maxOffset);
    }

    // --- 数据访问接口 (供逻辑层使用) ---

    public List<WarehouseEntry> getStorageList() {
        return storage;
    }

    public Map<FluidVariant, Long> getFluidStorageMap() {
        return fluidStorage;
    }

    public void markDirty() {
        this.baseCache = null;
        this.filteredCache = null;
        this.collapsedCache = null;
        this.sortedCache = null;
        if (onChanged != null) {
            onChanged.accept(this);
        }
    }

    /**
     * 仅使 UI 缓存失效，不触发 CCA 同步和持久化
     */
    public void markUIChanged() {
        // UI 状态改变（如搜索、排序切换）通常只需要从 filtered 级开始失效
        this.filteredCache = null;
        this.collapsedCache = null;
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
        // 滚动不触发任何缓存失效
    }

    public int getVisibleRows() { return visibleRows; }
    public void setVisibleRows(int rows) {
        this.visibleRows = Math.clamp(rows, 1, 12);
        this.scrollOffset = 0;
        this.markDirty(); // 布局改变建议全局更新
    }

    public boolean isFolded() { return isFolded; }
    public void setFolded(boolean folded) {
        if (!enabled && !folded) return;
        this.isFolded = folded;
        // 折叠不影响数据，仅影响渲染，不触发缓存失效
    }

    public int getSortMode() { return sortMode; }
    public void setSortMode(int mode) { 
        this.sortMode = mode; 
        this.sortedCache = null; // 仅使最后一级缓存失效
    }

    public boolean isAscending() { return isAscending; }
    public void setAscending(boolean ascending) { 
        this.isAscending = ascending; 
        this.sortedCache = null; // 仅使最后一级缓存失效
    }

    public boolean isQuickInteraction() { return quickInteraction; }
    public void setQuickInteraction(boolean quick) { this.quickInteraction = quick; }

    public boolean isSmartCollapse() { return smartCollapse; }
    public void setSmartCollapse(boolean smart) { 
        this.smartCollapse = smart; 
        this.collapsedCache = null; 
        this.sortedCache = null; 
    }

    public boolean isCraftRefill() { return craftRefill; }
    public void setCraftRefill(boolean refill) { this.craftRefill = refill; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSearchText() { return searchText; }
    public void setSearchText(String text) {
        String lower = text.toLowerCase();
        if (!lower.equals(this.searchText)) {
            this.searchText = lower;
            this.scrollOffset = 0;
            this.filteredCache = null;
            this.collapsedCache = null;
            this.sortedCache = null;
        }
    }

    public long getRealCount(int slotIndex) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        return (actualIndex >= 0 && actualIndex < sorted.size()) ? sorted.get(actualIndex).getCount() : 0;
    }

    // --- 排序与缓存逻辑 ---

    public List<WarehouseEntry> getSortedEntries() {
        // 第一级：基础缓存 (Raw Items + Fluids)
        if (baseCache == null) {
            baseCache = new ArrayList<>(storage);
            for (Map.Entry<FluidVariant, Long> entry : fluidStorage.entrySet()) {
                net.minecraft.world.item.Item virtualItem = getVirtualItemForFluid(entry.getKey());
                if (virtualItem != null) {
                    long bucketCount = entry.getValue() / FluidConstants.BUCKET;
                    if (bucketCount > 0) {
                        baseCache.add(new WarehouseEntry(new ItemStack(virtualItem), bucketCount));
                    }
                }
            }
            // 基础层变动，下游全部失效
            filteredCache = null;
            collapsedCache = null;
            sortedCache = null;
        }

        // 第二级：搜索过滤缓存
        if (filteredCache == null) {
            if (searchText.isEmpty()) {
                filteredCache = baseCache;
            } else {
                String query = searchText.toLowerCase().trim();
                boolean startExact = query.startsWith("!");
                boolean endExact = query.endsWith("!");
                
                String tempQuery = query;
                if (startExact) tempQuery = tempQuery.substring(1);
                if (endExact && tempQuery.length() > 0) tempQuery = tempQuery.substring(0, tempQuery.length() - 1);
                
                final String finalQuery = tempQuery;
                filteredCache = baseCache.stream()
                        .filter(entry -> matchesQuery(entry, finalQuery, startExact, endExact))
                        .toList();
            }
            collapsedCache = null;
            sortedCache = null;
        }

        // 第三级：智能折叠缓存
        if (collapsedCache == null) {
            if (smartCollapse && searchText.isEmpty()) {
                collapsedCache = applySmartCollapse(filteredCache);
            } else {
                collapsedCache = filteredCache;
            }
            sortedCache = null;
        }

        // 第四级：最终排序缓存
        if (sortedCache == null) {
            sortedCache = new ArrayList<>(collapsedCache);
            applySorting(sortedCache);
        }

        return sortedCache;
    }

    private boolean matchesQuery(WarehouseEntry entry, String finalQuery, boolean startExact, boolean endExact) {
        ItemStack stack = entry.getItemStack();
        String name = stack.getHoverName().getString().toLowerCase();
        if (checkMatch(name, finalQuery, startExact, endExact)) return true;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        if (checkMatch(id, finalQuery, startExact, endExact)) return true;

        net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String lineText = line.getString().toLowerCase();
                if (checkMatch(lineText, finalQuery, startExact, endExact)) return true;
            }
        }
        return false;
    }

    private boolean checkMatch(String target, String query, boolean startExact, boolean endExact) {
        if (query.isEmpty()) return true;
        if (startExact && endExact) return target.equals(query);
        if (startExact) return target.startsWith(query);
        if (endExact) return target.endsWith(query);
        return target.contains(query);
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

        // 升级系统
        upgradeStorage.clear();
        if (tag.contains("upgrades")) {
            ListTag upgradeList = tag.getList("upgrades", Tag.TAG_COMPOUND);
            for (int i = 0; i < upgradeList.size(); i++) {
                CompoundTag uTag = upgradeList.getCompound(i);
                ResourceLocation id = ResourceLocation.parse(uTag.getString("id"));
                ItemStack stack = ItemStack.parseOptional(registries, uTag.getCompound("item"));
                if (!stack.isEmpty()) {
                    upgradeStorage.put(id, stack);
                }
            }
        }
        this.upgradeScrollOffset = tag.getInt("upgradeScrollOffset");

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

        // 升级系统
        ListTag upgradeList = new ListTag();
        for (Map.Entry<ResourceLocation, ItemStack> entry : upgradeStorage.entrySet()) {
            CompoundTag uTag = new CompoundTag();
            uTag.putString("id", entry.getKey().toString());
            uTag.put("item", entry.getValue().save(registries));
            upgradeList.add(uTag);
        }
        tag.put("upgrades", upgradeList);
        tag.putInt("upgradeScrollOffset", upgradeScrollOffset);
    }
}
