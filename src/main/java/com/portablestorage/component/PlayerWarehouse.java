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
import java.util.Objects;

/**
 * 玩家仓库数据组件 (CCA Component)
 * 仅负责数据的持有、持久化 (NBT) 和基本的数据存取接口。
 * 复杂的业务逻辑（存取规则、流体转换等）应放在 WarehouseManager 中。
 */
public class PlayerWarehouse extends SnapshotParticipant<Map<FluidVariant, Long>> implements Container, Storage<FluidVariant> {
    public enum WarehouseType {
        NONE, BASE, FULL
    }

    private final List<WarehouseEntry> storage = new ArrayList<>();
    private final Map<FluidVariant, Long> fluidStorage = new LinkedHashMap<>();

    // 升级系统数据
    private final Map<ResourceLocation, ItemStack> upgradeStorage = new LinkedHashMap<>();
    private int upgradeScrollOffset = 0;
    private WarehouseType type = WarehouseType.NONE;
    private final List<String> hopperFilters = new ArrayList<>();
    private boolean hopperFilterBlacklist = true;
    private final List<String> foodFilters = new ArrayList<>();
    private boolean foodFilterBlacklist = true;
    private long experience = 0; // 瓶装经验 (XP points)

    // 裂隙升级数据
    private ResourceLocation riftReturnDim = null;
    private net.minecraft.core.BlockPos riftReturnPos = null;
    private float riftReturnYaw = 0;
    private float riftReturnPitch = 0;
    private int riftPlotX = Integer.MIN_VALUE;
    private int riftPlotZ = Integer.MIN_VALUE;
    private boolean riftInitialized = false;
    private net.minecraft.core.BlockPos riftLastPos = null;
    private float riftLastYaw = 0;
    private float riftLastPitch = 0;
    private UUID avatarUuid = null;

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
            List<UpgradeType> all = UpgradeRegistry.getAllUpgrades();
            int actualIndex = slot + upgradeScrollOffset;
            if (actualIndex >= 0 && actualIndex < all.size()) {
                ResourceLocation id = all.get(actualIndex).getId();
                ItemStack stack = upgradeStorage.get(id);
                if (stack != null && !stack.isEmpty()) {
                    ItemStack result = stack.split(amount);
                    if (stack.isEmpty()) {
                        setUpgrade(id, ItemStack.EMPTY);
                    } else {
                        markDirty();
                    }
                    return result;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 64);
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
    private boolean enabled = false;

    // 多级缓存
    private List<WarehouseEntry> baseCache = null;      // 原始项 + 流体
    private List<WarehouseEntry> filteredCache = null;  // 搜索过滤后
    private List<WarehouseEntry> collapsedCache = null; // 智能折叠后
    private List<WarehouseEntry> sortedCache = null;    // 最终排序后

    private final Consumer<PlayerWarehouse> onChanged;
    private final UUID ownerUuid;
    private WarehouseComponent parentComponent;

    public PlayerWarehouse(UUID id, Consumer<PlayerWarehouse> onChanged) {
        this.ownerUuid = id;
        this.onChanged = onChanged;
        // 如果服务端配置为无条件开启，则初始设为启用状态
        if (!"NONE".equals(com.portablestorage.config.ModConfig.unconditionalWarehouse)) {
            this.enabled = true;
        }
    }

    public void setParentComponent(WarehouseComponent parent) {
        this.parentComponent = parent;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public UUID getBarrelOwnerUuid() {
        ItemStack barrel = getUpgrade(com.portablestorage.upgrade.BarrelUpgrade.ID);
        if (!barrel.isEmpty() && barrel.is(com.portablestorage.item.ModItems.BOUND_BARREL)) {
            net.minecraft.world.item.component.CustomData customData = barrel.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null && customData.copyTag().hasUUID("owner")) {
                return customData.copyTag().getUUID("owner");
            }
        }
        return null;
    }

    public List<PlayerWarehouse> getSharedGroupWarehouses() {
        List<PlayerWarehouse> group = new ArrayList<>();
        group.add(this);

        if (getEffectiveType() != WarehouseType.FULL || parentComponent == null) {
            return group;
        }

        Set<UUID> groupUuids = new HashSet<>();
        groupUuids.add(this.ownerUuid);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (PlayerWarehouse pw : parentComponent.getAllWarehouses()) {
                if (pw.getEffectiveType() != WarehouseType.FULL) continue;
                
                UUID owner = pw.getOwnerUuid();
                UUID target = pw.getBarrelOwnerUuid();

                if (groupUuids.contains(owner)) {
                    if (target != null && groupUuids.add(target)) {
                        changed = true;
                    }
                }
                if (target != null && groupUuids.contains(target)) {
                    if (groupUuids.add(owner)) {
                        changed = true;
                    }
                }
            }
        }

        groupUuids.remove(this.ownerUuid);
        for (UUID uuid : groupUuids) {
            PlayerWarehouse pw = parentComponent.getWarehouse(uuid);
            if (pw != null) group.add(pw);
        }

        return group;
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
            upgradeStorage.put(id, stack.copy());
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

    public List<String> getHopperFilters() {
        return hopperFilters;
    }

    public boolean isHopperFilterBlacklist() {
        return hopperFilterBlacklist;
    }

    public void setHopperFilters(List<String> filters, boolean blacklist) {
        this.hopperFilters.clear();
        this.hopperFilters.addAll(filters);
        this.hopperFilterBlacklist = blacklist;
        this.markDirty();
    }

    public List<String> getFoodFilters() {
        return foodFilters;
    }

    public boolean isFoodFilterBlacklist() {
        return foodFilterBlacklist;
    }

    public void setFoodFilters(List<String> filters, boolean blacklist) {
        this.foodFilters.clear();
        this.foodFilters.addAll(filters);
        this.foodFilterBlacklist = blacklist;
        this.markDirty();
    }

    // --- 经验系统接口 ---

    public long getExperience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = experience;
        this.markDirty();
    }

    public void addExperience(long amount) {
        this.experience += amount;
        this.markDirty();
    }

    // --- 裂隙升级数据接口 ---

    public ResourceLocation getRiftReturnDim() { return riftReturnDim; }
    public void setRiftReturnDim(ResourceLocation dim) { this.riftReturnDim = dim; markDirty(); }

    public net.minecraft.core.BlockPos getRiftReturnPos() { return riftReturnPos; }
    public void setRiftReturnPos(net.minecraft.core.BlockPos pos) { this.riftReturnPos = pos; markDirty(); }

    public float getRiftReturnYaw() { return riftReturnYaw; }
    public void setRiftReturnYaw(float yaw) { this.riftReturnYaw = yaw; markDirty(); }

    public float getRiftReturnPitch() { return riftReturnPitch; }
    public void setRiftReturnPitch(float pitch) { this.riftReturnPitch = pitch; markDirty(); }

    public int getRiftPlotX() { return riftPlotX; }
    public void setRiftPlotX(int x) { this.riftPlotX = x; markDirty(); }

    public int getRiftPlotZ() { return riftPlotZ; }
    public void setRiftPlotZ(int z) { this.riftPlotZ = z; markDirty(); }

    public boolean isRiftInitialized() { return riftInitialized; }
    public void setRiftInitialized(boolean initialized) { this.riftInitialized = initialized; markDirty(); }

    public net.minecraft.core.BlockPos getRiftLastPos() { return riftLastPos; }
    public void setRiftLastPos(net.minecraft.core.BlockPos pos) { this.riftLastPos = pos; markDirty(); }

    public float getRiftLastYaw() { return riftLastYaw; }
    public void setRiftLastYaw(float yaw) { this.riftLastYaw = yaw; markDirty(); }

    public float getRiftLastPitch() { return riftLastPitch; }
    public void setRiftLastPitch(float pitch) { this.riftLastPitch = pitch; markDirty(); }

    public UUID getAvatarUuid() { return avatarUuid; }
    public void setAvatarUuid(UUID uuid) { this.avatarUuid = uuid; markDirty(); }

    public boolean hasRiftPlot() { return riftPlotX != Integer.MIN_VALUE; }

    // --- 数据访问接口 (供逻辑层使用) ---

    public List<WarehouseEntry> getStorageList() {
        return storage;
    }

    public Map<FluidVariant, Long> getFluidStorageMap() {
        return fluidStorage;
    }

    public void markDirty() {
        markDirtyInternal(new HashSet<>());
        if (onChanged != null) {
            onChanged.accept(this);
        }
    }

    private void markDirtyInternal(Set<UUID> visited) {
        if (!visited.add(this.ownerUuid)) return;

        this.baseCache = null;
        this.filteredCache = null;
        this.collapsedCache = null;
        this.sortedCache = null;

        // 通知组内其他成员失效缓存
        if (parentComponent != null) {
            for (PlayerWarehouse pw : getSharedGroupWarehouses()) {
                if (pw != this) {
                    pw.baseCache = null;
                    pw.filteredCache = null;
                    pw.collapsedCache = null;
                    pw.sortedCache = null;
                }
            }
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
        
        // 卸载并清除所有升级物品
        for (Map.Entry<ResourceLocation, ItemStack> entry : new HashMap<>(upgradeStorage).entrySet()) {
            UpgradeType type = UpgradeRegistry.get(entry.getKey());
            if (type != null && !entry.getValue().isEmpty()) {
                type.onUninstall(this, entry.getValue());
            }
        }
        upgradeStorage.clear();
        
        this.markDirty();
            }

    // --- Storage<FluidVariant> 接口实现 ---

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) return 0;
        
        List<PlayerWarehouse> group = getSharedGroupWarehouses();
        long totalInserted = 0;
        long remaining = maxAmount;

        for (PlayerWarehouse pw : group) {
            if (remaining <= 0) break;
            long inserted = pw.insertInternal(resource, remaining, transaction);
            totalInserted += inserted;
            remaining -= inserted;
        }

        return totalInserted;
    }

    private long insertInternal(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        // 无限流体检查
        if (isInfinite(resource, fluidStorage.getOrDefault(resource, 0L))) {
            return maxAmount; // 假装存入了，但实际不改变数值
        }

        updateSnapshots(transaction);
        long current = fluidStorage.getOrDefault(resource, 0L);
        fluidStorage.put(resource, current + maxAmount);
        this.markDirty();
        return maxAmount;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0) return 0;
        
        List<PlayerWarehouse> group = getSharedGroupWarehouses();
        long totalExtracted = 0;
        long remaining = maxAmount;

        for (PlayerWarehouse pw : group) {
            if (remaining <= 0) break;
            long extracted = pw.extractInternal(resource, remaining, transaction);
            totalExtracted += extracted;
            remaining -= extracted;
        }

        return totalExtracted;
    }

    private long extractInternal(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        long current = fluidStorage.getOrDefault(resource, 0L);
        if (current <= 0) return 0;

        // 无限流体检查
        if (isInfinite(resource, current)) {
            return maxAmount; // 假装提取了，但实际不改变数值
        }

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

    private boolean isInfinite(FluidVariant fluid, long amount) {
        if (fluid.isOf(Fluids.LAVA)) {
            long threshold = com.portablestorage.config.ModConfig.lavaInfiniteThreshold;
            return threshold >= 0 && amount >= threshold * FluidConstants.BUCKET;
        }
        if (fluid.isOf(Fluids.WATER)) {
            long threshold = com.portablestorage.config.ModConfig.waterInfiniteThreshold;
            return threshold >= 0 && amount >= threshold * FluidConstants.BUCKET;
        }
        return false;
    }

    @Override
    public Iterator<StorageView<FluidVariant>> iterator() {
        List<PlayerWarehouse> group = getSharedGroupWarehouses();
        List<StorageView<FluidVariant>> views = new ArrayList<>();
        
        // 聚合所有仓库的流体视图
        Map<FluidVariant, Long> merged = new LinkedHashMap<>();
        for (PlayerWarehouse pw : group) {
            for (Map.Entry<FluidVariant, Long> e : pw.fluidStorage.entrySet()) {
                merged.put(e.getKey(), merged.getOrDefault(e.getKey(), 0L) + e.getValue());
            }
        }

        for (Map.Entry<FluidVariant, Long> e : merged.entrySet()) {
            views.add(new StorageView<FluidVariant>() {
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
            });
        }
        return views.iterator();
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

    public void togglePinned(int slotIndex) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            WarehouseEntry entry = sorted.get(actualIndex);
            boolean newState = !entry.isPinned();
            
            // 如果是折叠后的项，我们需要找到所有原始项并同步状态
            net.minecraft.world.item.component.CustomData customData = entry.getItemStack().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            boolean isCollapsed = customData != null && customData.copyTag().getBoolean(com.portablestorage.util.WarehouseConstants.SMART_COLLAPSE_TAG);
            
            if (isCollapsed) {
                for (WarehouseEntry e : storage) {
                    if (e.getItemStack().getItem() == entry.getItemStack().getItem()) {
                        e.setPinned(newState);
                    }
                }
            } else {
                entry.setPinned(newState);
            }
            markDirty();
        }
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

    public boolean isEnabled() { 
        if (!enabled) return false;
        WarehouseType effectiveType = getEffectiveType();
        return effectiveType != WarehouseType.NONE;
    }

    public WarehouseType getType() { return type; }
    public void setType(WarehouseType type) { this.type = type; markDirty(); }

    public WarehouseType getEffectiveType() {
        String configType = com.portablestorage.config.ModConfig.unconditionalWarehouse;
        if ("FULL".equals(configType)) return WarehouseType.FULL;
        if ("BASE".equals(configType)) return WarehouseType.BASE;
        return this.type;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; markDirty(); }

    public int getMaxStorageTypes() {
        if (getEffectiveType() == WarehouseType.BASE) return com.portablestorage.config.ModConfig.baseMaxStorageTypes;
        return com.portablestorage.config.ModConfig.maxStorageTypes;
    }

    public long getMaxItemStackSize() {
        if (getEffectiveType() == WarehouseType.BASE) return com.portablestorage.config.ModConfig.baseMaxItemStackSize;
        return com.portablestorage.config.ModConfig.maxItemStackSize;
    }

    /**
     * 检查是否安装了工作台升级
     */
    public boolean hasWorkbenchUpgrade() {
        return !getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty();
    }

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
            List<PlayerWarehouse> group = getSharedGroupWarehouses();
            
            // 聚合所有仓库的物品
            Map<WarehouseEntryKey, Long> mergedItems = new LinkedHashMap<>();
            Map<WarehouseEntryKey, Boolean> pinnedItems = new HashMap<>();
            Map<FluidVariant, Long> mergedFluids = new LinkedHashMap<>();
            long mergedExperience = 0;

            for (PlayerWarehouse pw : group) {
                // 物品
                for (WarehouseEntry entry : pw.getStorageList()) {
                    WarehouseEntryKey key = new WarehouseEntryKey(entry.getItemStack());
                    mergedItems.put(key, mergedItems.getOrDefault(key, 0L) + entry.getCount());
                    if (entry.isPinned()) pinnedItems.put(key, true);
                }
                // 流体
                for (Map.Entry<FluidVariant, Long> entry : pw.getFluidStorageMap().entrySet()) {
                    mergedFluids.put(entry.getKey(), mergedFluids.getOrDefault(entry.getKey(), 0L) + entry.getValue());
                }
                // 经验
                mergedExperience += pw.getExperience();
            }

            baseCache = new ArrayList<>();
            // 将聚合后的物品转回 WarehouseEntry
            for (Map.Entry<WarehouseEntryKey, Long> e : mergedItems.entrySet()) {
                WarehouseEntry entry = new WarehouseEntry(e.getKey().toStack(), e.getValue());
                if (pinnedItems.getOrDefault(e.getKey(), false)) entry.setPinned(true);
                baseCache.add(entry);
            }
            // 将聚合后的流体转回 WarehouseEntry
            for (Map.Entry<FluidVariant, Long> e : mergedFluids.entrySet()) {
                FluidVariant variant = e.getKey();
                long amount = e.getValue();
                boolean infinite = isInfinite(variant, amount);
                long bucketCount = infinite ? WarehouseConstants.INFINITE_COUNT : (amount / FluidConstants.BUCKET);
                
                if (bucketCount > 0 || infinite) {
                    net.minecraft.world.item.Item virtualItem = getVirtualItemForFluid(variant);
                    if (virtualItem != null) {
                        ItemStack fluidStack = new ItemStack(virtualItem);
                        fluidStack.applyComponents(variant.getComponents());
                        if (infinite) {
                            fluidStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                                net.minecraft.world.item.component.CustomData.of(new net.minecraft.nbt.CompoundTag() {{
                                    putBoolean(WarehouseConstants.INFINITE_TAG, true);
                                }}));
                        }
                        baseCache.add(new WarehouseEntry(fluidStack, bucketCount));
                    }
                }
            }
            
            // 经验系统 (如果组内有人开启了经验升级)
            boolean hasExperienceUpgrade = group.stream().anyMatch(pw -> !pw.getUpgrade(com.portablestorage.upgrade.ExperienceUpgrade.ID).isEmpty());
            if (hasExperienceUpgrade) {
                ItemStack xpStack = new ItemStack(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE);
                List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
                lore.add(net.minecraft.network.chat.Component.translatable("tooltip.portablestorage.experience_desc_1", mergedExperience).withStyle(net.minecraft.ChatFormatting.GRAY));
                lore.add(net.minecraft.network.chat.Component.literal(" "));
                lore.add(net.minecraft.network.chat.Component.translatable("tooltip.portablestorage.experience_desc_2").withStyle(net.minecraft.ChatFormatting.BLUE));
                
                // 取当前仓库的升级阶数显示，或者取组内最高？取当前的吧。
                ItemStack upgradeStack = getUpgrade(com.portablestorage.upgrade.ExperienceUpgrade.ID);
                int step = upgradeStack.isEmpty() ? 0 : com.portablestorage.upgrade.ExperienceUpgrade.getStep(upgradeStack);
                
                lore.add(net.minecraft.network.chat.Component.translatable("tooltip.portablestorage.experience_desc_3", step).withStyle(net.minecraft.ChatFormatting.GRAY));
                lore.add(net.minecraft.network.chat.Component.translatable("tooltip.portablestorage.experience_desc_4", step).withStyle(net.minecraft.ChatFormatting.GRAY));
                lore.add(net.minecraft.network.chat.Component.translatable("tooltip.portablestorage.experience_desc_5", step).withStyle(net.minecraft.ChatFormatting.GRAY));
                lore.add(net.minecraft.network.chat.Component.translatable("tooltip.portablestorage.experience_desc_6").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                
                xpStack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
                baseCache.add(new WarehouseEntry(xpStack, mergedExperience));
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
        return com.portablestorage.util.PinyinUtils.matches(target, query);
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
                boolean pinned = false;
                for (WarehouseEntry e : entries) {
                    totalCount += e.getCount();
                    lastUpdated = Math.max(lastUpdated, e.getLastUpdated());
                    if (e.isPinned()) pinned = true;
                }
                
                ItemStack displayStack = new ItemStack(group.getKey());
                displayStack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                displayStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                    net.minecraft.world.item.component.CustomData.of(new net.minecraft.nbt.CompoundTag() {{
                        putBoolean(com.portablestorage.util.WarehouseConstants.SMART_COLLAPSE_TAG, true);
                    }}));
                
                WarehouseEntry collapsedEntry = new WarehouseEntry(displayStack, totalCount);
                collapsedEntry.setPinned(pinned);
                collapsed.add(collapsedEntry);
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
        
        // 置顶逻辑：置顶项排在最前面
        Comparator<WarehouseEntry> finalComparator = Comparator.<WarehouseEntry, Boolean>comparing(WarehouseEntry::isPinned).reversed()
                .thenComparing(comparator)
                .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
        
        list.sort(finalComparator);
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
        this.type = tag.contains("activationType") ? WarehouseType.values()[tag.getInt("activationType")] : WarehouseType.NONE;
        this.experience = tag.getLong("experience");

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

        this.hopperFilters.clear();
        this.hopperFilterBlacklist = tag.getBoolean("hopperFilterBlacklist");
        if (tag.contains("hopperFilters")) {
            ListTag filterList = tag.getList("hopperFilters", Tag.TAG_STRING);
            for (int i = 0; i < filterList.size(); i++) {
                this.hopperFilters.add(filterList.getString(i));
            }
        } else {
            // 默认黑名单
            this.hopperFilterBlacklist = true;
        }

        this.foodFilters.clear();
        this.foodFilterBlacklist = tag.getBoolean("foodFilterBlacklist");
        if (tag.contains("foodFilters")) {
            ListTag filterList = tag.getList("foodFilters", Tag.TAG_STRING);
            for (int i = 0; i < filterList.size(); i++) {
                this.foodFilters.add(filterList.getString(i));
            }
        } else {
            // 默认黑名单
            this.foodFilterBlacklist = true;
        }

        // 裂隙升级数据
        if (tag.contains("riftReturnDim")) {
            this.riftReturnDim = ResourceLocation.parse(tag.getString("riftReturnDim"));
            this.riftReturnPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "riftReturnPos").orElse(null);
            this.riftReturnYaw = tag.getFloat("riftReturnYaw");
            this.riftReturnPitch = tag.getFloat("riftReturnPitch");
        } else {
            this.riftReturnDim = null;
            this.riftReturnPos = null;
        }
        this.riftPlotX = tag.contains("riftPlotX") ? tag.getInt("riftPlotX") : Integer.MIN_VALUE;
        this.riftPlotZ = tag.contains("riftPlotZ") ? tag.getInt("riftPlotZ") : Integer.MIN_VALUE;
        this.riftInitialized = tag.getBoolean("riftInitialized");
        this.riftLastPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "riftLastPos").orElse(null);
        this.riftLastYaw = tag.getFloat("riftLastYaw");
        this.riftLastPitch = tag.getFloat("riftLastPitch");
        if (tag.hasUUID("avatarUuid")) {
            this.avatarUuid = tag.getUUID("avatarUuid");
        } else {
            this.avatarUuid = null;
        }

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
        tag.putInt("activationType", type.ordinal());
        tag.putLong("experience", experience);

        // 升级系统
        ListTag upgradeList = new ListTag();
        for (Map.Entry<ResourceLocation, ItemStack> entry : upgradeStorage.entrySet()) {
            CompoundTag uTag = new CompoundTag();
            uTag.putString("id", entry.getKey().toString());
            uTag.put("item", entry.getValue().saveOptional(registries)); // 使用 saveOptional
            upgradeList.add(uTag);
        }
        tag.put("upgrades", upgradeList);
        tag.putInt("upgradeScrollOffset", upgradeScrollOffset);

        ListTag filterList = new ListTag();
        for (String filter : hopperFilters) {
            filterList.add(net.minecraft.nbt.StringTag.valueOf(filter));
        }
        tag.put("hopperFilters", filterList);
        tag.putBoolean("hopperFilterBlacklist", hopperFilterBlacklist);

        ListTag foodFilterList = new ListTag();
        for (String filter : foodFilters) {
            foodFilterList.add(net.minecraft.nbt.StringTag.valueOf(filter));
        }
        tag.put("foodFilters", foodFilterList);
        tag.putBoolean("foodFilterBlacklist", foodFilterBlacklist);

        // 裂隙升级数据
        if (riftReturnDim != null) {
            tag.putString("riftReturnDim", riftReturnDim.toString());
            if (riftReturnPos != null) {
                tag.put("riftReturnPos", net.minecraft.nbt.NbtUtils.writeBlockPos(riftReturnPos));
            }
            tag.putFloat("riftReturnYaw", riftReturnYaw);
            tag.putFloat("riftReturnPitch", riftReturnPitch);
        }
        tag.putInt("riftPlotX", riftPlotX);
        tag.putInt("riftPlotZ", riftPlotZ);
        tag.putBoolean("riftInitialized", riftInitialized);
        if (riftLastPos != null) {
            tag.put("riftLastPos", net.minecraft.nbt.NbtUtils.writeBlockPos(riftLastPos));
            tag.putFloat("riftLastYaw", riftLastYaw);
            tag.putFloat("riftLastPitch", riftLastPitch);
        }
        if (avatarUuid != null) {
            tag.putUUID("avatarUuid", avatarUuid);
        }
    }

    private static class WarehouseEntryKey {
        private final net.minecraft.world.item.Item item;
        private final net.minecraft.core.component.DataComponentPatch patch;

        public WarehouseEntryKey(ItemStack stack) {
            this.item = stack.getItem();
            this.patch = stack.getComponentsPatch();
        }

        public ItemStack toStack() {
            ItemStack stack = new ItemStack(item);
            stack.applyComponents(patch);
            return stack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WarehouseEntryKey that = (WarehouseEntryKey) o;
            return item == that.item && Objects.equals(patch, that.patch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, patch);
        }
    }
}
