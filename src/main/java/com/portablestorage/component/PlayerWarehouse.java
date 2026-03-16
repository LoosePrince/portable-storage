package com.portablestorage.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.upgrade.UpgradeRegistry;
import com.portablestorage.upgrade.UpgradeType;
import com.portablestorage.util.WarehouseConstants;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * 玩家仓库数据组件 (CCA Component)
 * 仅负责数据的持有、持久化 (NBT) 和基本的数据存取接口。
 * 复杂的业务逻辑（存取规则、流体转换等）应放在 WarehouseManager 中。
 */
public class PlayerWarehouse extends SnapshotParticipant<Map<FluidVariant, Long>> implements Container {
    public enum WarehouseType {
        NONE, BASE, FULL
    }

    private final List<WarehouseEntry> storage = new ArrayList<>();
    private final Map<FluidVariant, Long> fluidStorage = new LinkedHashMap<>();

    // 升级系统数据
    private final Map<Identifier, ItemStack> upgradeStorage = new LinkedHashMap<>();
    private int upgradeScrollOffset = 0;
    private WarehouseType type = WarehouseType.NONE;
    private final List<String> hopperFilters = new ArrayList<>();
    private boolean hopperFilterBlacklist = true;
    private final List<String> foodFilters = new ArrayList<>();
    private boolean foodFilterBlacklist = true;
    private final Set<UUID> forbiddenPlayers = new HashSet<>();
    private long experience = 0; // 瓶装经验 (XP points)

    // 裂隙升级数据
    private Identifier riftReturnDim = null;
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
                Identifier id = all.get(actualIndex).getId();
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
    private List<WarehouseEntry> baseCache = null; // 原始项 + 流体
    private List<WarehouseEntry> filteredCache = null; // 搜索过滤后
    private List<WarehouseEntry> collapsedCache = null; // 智能折叠后
    private List<WarehouseEntry> sortedCache = null; // 最终排序后
    private List<WarehouseEntry> frozenCache = null; // 锁定渲染缓存

    private final Consumer<PlayerWarehouse> onChanged;
    private final UUID ownerUuid;
    private String ownerName = "Unknown";
    private WarehouseComponent parentComponent;

    public PlayerWarehouse(UUID id, Consumer<PlayerWarehouse> onChanged) {
        this.ownerUuid = id;
        this.onChanged = onChanged;
        // 如果服务端配置为无条件开启，则初始设为启用状态
        if (!"NONE".equals(com.portablestorage.config.ModConfig.unconditionalWarehouse)) {
            this.enabled = true;
        }
    }

    public boolean isFrozen() {
        return frozenCache != null;
    }

    public void setFrozen(boolean frozen) {
        if (frozen) {
            if (frozenCache == null) {
                // 锁定当前排序后的列表快照
                List<WarehouseEntry> current = getSortedEntries();
                frozenCache = new ArrayList<>();
                for (WarehouseEntry entry : current) {
                    // 创建条目快照，初始数量保持一致
                    WarehouseEntry snapshot = new WarehouseEntry(entry.getItemStack(), entry.getCount());
                    snapshot.setPinned(entry.isPinned());
                    snapshot.setLastUpdated(entry.getLastUpdated());
                    frozenCache.add(snapshot);
                }
            }
        } else {
            frozenCache = null;
            // 解冻时使缓存失效以触发一次全量重排
            markDirty();
        }
    }

    public void setParentComponent(WarehouseComponent parent) {
        this.parentComponent = parent;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String name) {
        if (!Objects.equals(this.ownerName, name)) {
            this.ownerName = name;
            markDirty();
        }
    }

    public UUID getBarrelOwnerUuid() {
        ItemStack barrel = getUpgrade(com.portablestorage.upgrade.BarrelUpgrade.ID);
        if (!barrel.isEmpty() && barrel.is(com.portablestorage.item.ModItems.BOUND_BARREL)) {
            net.minecraft.world.item.component.CustomData customData = barrel
                    .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                if (tag.contains("owner")) {
                    java.util.Optional<String> ownerStrOpt = tag.getString("owner");
                    if (ownerStrOpt.isPresent()) {
                        try {
                            return java.util.UUID.fromString(ownerStrOpt.get());
                        } catch (IllegalArgumentException ignored) {
                            // 无效 UUID，视为未绑定
                        }
                    }
                }
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

        UUID myUuid = this.ownerUuid;
        UUID myTarget = this.getBarrelOwnerUuid();

        // 检查自己是否正在提供共享给别人
        boolean amIProviding = false;
        for (PlayerWarehouse pw : parentComponent.getAllWarehouses()) {
            if (pw == this || pw.getEffectiveType() != WarehouseType.FULL)
                continue;
            if (myUuid.equals(pw.getBarrelOwnerUuid())) {
                amIProviding = true;
                break;
            }
        }

        // 如果发生连锁共享（既是提供者又是消费者），则所有外部连接失效
        if (myTarget != null && amIProviding) {
            return group; // 仅保留自己
        }

        // 构建共享组
        // 模式 A：我是消费者（我持有别人的木桶），且对方不是消费者（对方没有木桶）
        if (myTarget != null) {
            PlayerWarehouse provider = parentComponent.getWarehouse(myTarget);
            if (provider != null && provider.getEffectiveType() == WarehouseType.FULL) {
                // 对方必须纯粹是提供者（不能持有别人的木桶）
                if (provider.getBarrelOwnerUuid() == null) {
                    // 检查屏蔽逻辑
                    if (!this.isForbidden(myTarget) && !provider.isForbidden(myUuid)) {
                        group.add(provider);

                        // 同时加入该提供者的所有其他信任的消费者
                        for (PlayerWarehouse other : parentComponent.getAllWarehouses()) {
                            if (other == this || other == provider || other.getEffectiveType() != WarehouseType.FULL)
                                continue;
                            if (myTarget.equals(other.getBarrelOwnerUuid())) {
                                if (!provider.isForbidden(other.getOwnerUuid()) && !other.isForbidden(myTarget)) {
                                    group.add(other);
                                }
                            }
                        }
                    }
                }
            }
        }
        // 模式 B：我是纯粹提供者（我没有持有别人的木桶），寻找所有持有我木桶的玩家
        else if (amIProviding) {
            for (PlayerWarehouse consumer : parentComponent.getAllWarehouses()) {
                if (consumer == this || consumer.getEffectiveType() != WarehouseType.FULL)
                    continue;
                if (myUuid.equals(consumer.getBarrelOwnerUuid())) {
                    // 该消费者不能同时也持有别人的木桶（不能发生连锁）
                    // 虽然在逻辑上连锁的消费者会被模式 A 排除，但这里双向校验更稳健
                    if (!this.isForbidden(consumer.getOwnerUuid()) && !consumer.isForbidden(myUuid)) {
                        group.add(consumer);
                    }
                }
            }
        }

        return group;
    }

    /**
     * 检查是否存在连锁共享冲突
     */
    public boolean isSharingConflict() {
        if (parentComponent == null)
            return false;
        UUID myUuid = this.ownerUuid;
        UUID myTarget = this.getBarrelOwnerUuid();
        if (myTarget == null)
            return false;

        for (PlayerWarehouse pw : parentComponent.getAllWarehouses()) {
            if (pw == this || pw.getEffectiveType() != WarehouseType.FULL)
                continue;
            if (myUuid.equals(pw.getBarrelOwnerUuid())) {
                return true; // 既持有别人的桶，又有人持有我的桶
            }
        }
        return false;
    }

    // ========== 升级系统接口 ==========

    public ItemStack getUpgrade(Identifier id) {
        return upgradeStorage.getOrDefault(id, ItemStack.EMPTY);
    }

    public void setUpgrade(Identifier id, ItemStack stack) {
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

    public Map<Identifier, ItemStack> getUpgradeStorage() {
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

    public boolean isForbidden(UUID uuid) {
        return forbiddenPlayers.contains(uuid);
    }

    public void setForbidden(UUID uuid, boolean forbidden) {
        if (forbidden) {
            if (forbiddenPlayers.add(uuid))
                markDirty();
        } else {
            if (forbiddenPlayers.remove(uuid))
                markDirty();
        }
    }

    public Set<UUID> getForbiddenPlayers() {
        return Collections.unmodifiableSet(forbiddenPlayers);
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

    // ========== 经验系统接口 ==========

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

    // ========== 裂隙升级数据接口 ==========

    public Identifier getRiftReturnDim() {
        return riftReturnDim;
    }

    public void setRiftReturnDim(Identifier dim) {
        this.riftReturnDim = dim;
        markDirty();
    }

    public net.minecraft.core.BlockPos getRiftReturnPos() {
        return riftReturnPos;
    }

    public void setRiftReturnPos(net.minecraft.core.BlockPos pos) {
        this.riftReturnPos = pos;
        markDirty();
    }

    public float getRiftReturnYaw() {
        return riftReturnYaw;
    }

    public void setRiftReturnYaw(float yaw) {
        this.riftReturnYaw = yaw;
        markDirty();
    }

    public float getRiftReturnPitch() {
        return riftReturnPitch;
    }

    public void setRiftReturnPitch(float pitch) {
        this.riftReturnPitch = pitch;
        markDirty();
    }

    public int getRiftPlotX() {
        return riftPlotX;
    }

    public void setRiftPlotX(int x) {
        this.riftPlotX = x;
        markDirty();
    }

    public int getRiftPlotZ() {
        return riftPlotZ;
    }

    public void setRiftPlotZ(int z) {
        this.riftPlotZ = z;
        markDirty();
    }

    public boolean isRiftInitialized() {
        return riftInitialized;
    }

    public void setRiftInitialized(boolean initialized) {
        this.riftInitialized = initialized;
        markDirty();
    }

    public net.minecraft.core.BlockPos getRiftLastPos() {
        return riftLastPos;
    }

    public void setRiftLastPos(net.minecraft.core.BlockPos pos) {
        this.riftLastPos = pos;
        markDirty();
    }

    public float getRiftLastYaw() {
        return riftLastYaw;
    }

    public void setRiftLastYaw(float yaw) {
        this.riftLastYaw = yaw;
        markDirty();
    }

    public float getRiftLastPitch() {
        return riftLastPitch;
    }

    public void setRiftLastPitch(float pitch) {
        this.riftLastPitch = pitch;
        markDirty();
    }

    public UUID getAvatarUuid() {
        return avatarUuid;
    }

    public void setAvatarUuid(UUID uuid) {
        this.avatarUuid = uuid;
        markDirty();
    }

    public boolean hasRiftPlot() {
        return riftPlotX != Integer.MIN_VALUE;
    }

    // ========== 数据访问接口（供逻辑层使用）==========

    /**
     * 获取存储列表
     * 
     * @return 仓库条目列表
     */
    public List<WarehouseEntry> getStorageList() {
        return storage;
    }

    /**
     * 获取流体存储映射表
     * 
     * @return 流体变体到数量的映射
     */
    public Map<FluidVariant, Long> getFluidStorageMap() {
        return fluidStorage;
    }

    /**
     * 获取指定流体的数量
     * 
     * @param variant 流体变体
     * @return 流体数量
     */
    public long getFluidAmount(FluidVariant variant) {
        return fluidStorage.getOrDefault(variant, 0L);
    }

    /**
     * 标记数据已修改，触发缓存失效和同步
     */
    public void markDirty() {
        markDirtyInternal(new HashSet<>());
        if (onChanged != null) {
            onChanged.accept(this);
        }
    }

    /**
     * 内部标记脏数据方法，防止循环引用
     * 
     * @param visited 已访问的 UUID 集合
     */
    private void markDirtyInternal(Set<UUID> visited) {
        if (!visited.add(this.ownerUuid))
            return;

        this.baseCache = null;
        this.filteredCache = null;
        this.collapsedCache = null;
        this.sortedCache = null;

        // 通知共享组内其他成员失效缓存
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
     * UI 状态改变（如搜索、排序切换）通常只需要从 filtered 级开始失效
     */
    public void markUIChanged() {
        this.filteredCache = null;
        this.collapsedCache = null;
        this.sortedCache = null;
    }

    /** 清除所有缓存（含 baseCache），不触发 onChanged。供客户端应用服务端 pinned 更新后刷新显示。 */
    public void invalidateAllCaches() {
        this.baseCache = null;
        this.filteredCache = null;
        this.collapsedCache = null;
        this.sortedCache = null;
    }

    // ========== Container 接口实现 ==========

    @Override
    public int getContainerSize() {
        return visibleRows * 9;
    }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty() && fluidStorage.isEmpty();
    }

    /**
     * 检查仓库是否完全为空（包括物品、流体、经验和升级）
     */
    public boolean isFullyEmpty() {
        return storage.isEmpty() && fluidStorage.isEmpty() && experience == 0 && upgradeStorage.isEmpty();
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
        // 仓库内容由自定义点击逻辑和 WarehouseManager 管理
        // 忽略原版容器的 setItem 调用，防止在同步过程中数量意外累加
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
        for (Map.Entry<Identifier, ItemStack> entry : new HashMap<>(upgradeStorage).entrySet()) {
            UpgradeType type = UpgradeRegistry.get(entry.getKey());
            if (type != null && !entry.getValue().isEmpty()) {
                type.onUninstall(this, entry.getValue());
            }
        }
        upgradeStorage.clear();

        this.markDirty();
    }

    @Override
    public java.util.Iterator<ItemStack> iterator() {
        List<WarehouseEntry> sorted = getSortedEntries();
        List<ItemStack> stacks = new ArrayList<>(sorted.size());
        for (WarehouseEntry entry : sorted) {
            stacks.add(entry.getItemStack());
        }
        return stacks.iterator();
    }

    // ========== 流体存储接口实现 (不再直接实现 Storage<FluidVariant>) ==========

    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0)
            return 0;

        List<PlayerWarehouse> group = getSharedGroupWarehouses();
        long totalInserted = 0;
        long remaining = maxAmount;

        for (PlayerWarehouse pw : group) {
            if (remaining <= 0)
                break;
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

    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (maxAmount <= 0)
            return 0;

        List<PlayerWarehouse> group = getSharedGroupWarehouses();
        long totalExtracted = 0;
        long remaining = maxAmount;

        for (PlayerWarehouse pw : group) {
            if (remaining <= 0)
                break;
            long extracted = pw.extractInternal(resource, remaining, transaction);
            totalExtracted += extracted;
            remaining -= extracted;
        }

        return totalExtracted;
    }

    private long extractInternal(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        long current = fluidStorage.getOrDefault(resource, 0L);
        if (current <= 0)
            return 0;

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

    public java.util.Iterator<StorageView<FluidVariant>> fluidIterator() {
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
                public boolean isResourceBlank() {
                    return e.getKey().isBlank();
                }

                @Override
                public FluidVariant getResource() {
                    return e.getKey();
                }

                @Override
                public long getAmount() {
                    return e.getValue();
                }

                @Override
                public long getCapacity() {
                    return Long.MAX_VALUE;
                }
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

    // ========== 状态控制 Getter/Setter ==========

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int offset) {
        int maxRows = (int) Math.ceil(getSortedEntries().size() / 9.0);
        int maxOffset = Math.max(0, maxRows - visibleRows);
        this.scrollOffset = Math.clamp(offset, 0, maxOffset);
        // 滚动不触发任何缓存失效
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public void setVisibleRows(int rows) {
        this.visibleRows = Math.clamp(rows, 1, 12);
        this.scrollOffset = 0;
        this.markDirty(); // 布局改变建议全局更新
    }

    public boolean isFolded() {
        return isFolded;
    }

    public void setFolded(boolean folded) {
        if (!enabled && !folded)
            return;
        this.isFolded = folded;
        // 折叠不影响数据，仅影响渲染，不触发缓存失效
    }

    public int getSortMode() {
        return sortMode;
    }

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
            net.minecraft.world.item.Item item = entry.getItemStack().getItem();
            for (WarehouseEntry e : storage) {
                if (e.getItemStack().getItem() == item)
                    e.setPinned(newState);
            }
            markDirty();
        }
    }

    public boolean isAscending() {
        return isAscending;
    }

    public void setAscending(boolean ascending) {
        this.isAscending = ascending;
        this.sortedCache = null; // 仅使最后一级缓存失效
    }

    public boolean isQuickInteraction() {
        return quickInteraction;
    }

    public void setQuickInteraction(boolean quick) {
        this.quickInteraction = quick;
    }

    public boolean isSmartCollapse() {
        return smartCollapse;
    }

    public void setSmartCollapse(boolean smart) {
        this.smartCollapse = smart;
        this.collapsedCache = null;
        this.sortedCache = null;
    }

    public boolean isCraftRefill() {
        return craftRefill;
    }

    public void setCraftRefill(boolean refill) {
        this.craftRefill = refill;
    }

    public boolean isEnabled() {
        if (!enabled)
            return false;
        WarehouseType effectiveType = getEffectiveType();
        return effectiveType != WarehouseType.NONE;
    }

    public WarehouseType getType() {
        return type;
    }

    public void setType(WarehouseType type) {
        this.type = type;
        markDirty();
    }

    public WarehouseType getEffectiveType() {
        String configType = com.portablestorage.config.ModConfig.unconditionalWarehouse;
        if ("FULL".equals(configType))
            return WarehouseType.FULL;
        if ("BASE".equals(configType))
            return WarehouseType.BASE;
        return this.type;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        markDirty();
    }

    public int getMaxStorageTypes() {
        if (getEffectiveType() == WarehouseType.BASE)
            return com.portablestorage.config.ModConfig.baseMaxStorageTypes;
        return com.portablestorage.config.ModConfig.maxStorageTypes;
    }

    public long getMaxItemStackSize() {
        if (getEffectiveType() == WarehouseType.BASE)
            return com.portablestorage.config.ModConfig.baseMaxItemStackSize;
        return com.portablestorage.config.ModConfig.maxItemStackSize;
    }

    /**
     * 检查是否安装了工作台升级
     */
    public boolean hasWorkbenchUpgrade() {
        return !getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty();
    }

    public String getSearchText() {
        return searchText;
    }

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
        List<WarehouseEntry> entries = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        if (actualIndex < 0 || actualIndex >= entries.size())
            return 0;

        WarehouseEntry entry = entries.get(actualIndex);
        if (frozenCache != null) {
            // 冻结模式：查询该物品在当前仓库组中的实时聚合数量
            return getLiveCount(entry.getItemStack());
        }
        return entry.getCount();
    }

    /**
     * 获取指定物品在整个共享组中的实时聚合数量
     */
    public long getLiveCount(ItemStack template) {
        if (getEffectiveType() == WarehouseType.NONE)
            return 0;

        long total = 0;
        // 特殊处理：流体
        if (WarehouseManager.isVirtualFluid(template.getItem())) {
            FluidVariant fluid = WarehouseManager.getFluidForVirtualItem(template.getItem());
            if (fluid != null) {
                for (PlayerWarehouse pw : getSharedGroupWarehouses()) {
                    total += pw.getFluidAmount(fluid);
                }
                return total / net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET;
            }
        }

        // 特殊处理：经验
        if (template.is(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE)) {
            for (PlayerWarehouse pw : getSharedGroupWarehouses()) {
                total += pw.getExperience();
            }
            return total;
        }

        // 普通物品
        for (PlayerWarehouse pw : getSharedGroupWarehouses()) {
            for (WarehouseEntry entry : pw.getStorageList()) {
                if (entry.matches(template)) {
                    total += entry.getCount();
                }
            }
        }
        return total;
    }

    // ========== 排序与缓存逻辑 ==========

    public List<WarehouseEntry> getSortedEntries() {
        if (frozenCache != null) {
            return frozenCache;
        }
        // 第一级：基础缓存（原始物品和流体）
        if (baseCache == null) {
            List<PlayerWarehouse> group = getSharedGroupWarehouses();

            // 聚合所有仓库的物品
            Map<WarehouseEntryKey, Long> mergedItems = new LinkedHashMap<>();
            Map<WarehouseEntryKey, Boolean> pinnedItems = new HashMap<>();
            Map<WarehouseEntryKey, Long> lastUpdatedMap = new HashMap<>();
            Map<FluidVariant, Long> mergedFluids = new LinkedHashMap<>();
            long mergedExperience = 0;

            for (PlayerWarehouse pw : group) {
                // 聚合物品
                for (WarehouseEntry entry : pw.getStorageList()) {
                    WarehouseEntryKey key = new WarehouseEntryKey(entry.getItemStack());
                    mergedItems.put(key, mergedItems.getOrDefault(key, 0L) + entry.getCount());
                    if (entry.isPinned())
                        pinnedItems.put(key, true);
                    // 保留最大的更新时间
                    lastUpdatedMap.put(key, Math.max(lastUpdatedMap.getOrDefault(key, 0L), entry.getLastUpdated()));
                }
                // 聚合流体
                for (Map.Entry<FluidVariant, Long> entry : pw.getFluidStorageMap().entrySet()) {
                    mergedFluids.put(entry.getKey(), mergedFluids.getOrDefault(entry.getKey(), 0L) + entry.getValue());
                }
                // 聚合经验
                mergedExperience += pw.getExperience();
            }

            baseCache = new ArrayList<>();
            // 将聚合后的物品转换为 WarehouseEntry
            for (Map.Entry<WarehouseEntryKey, Long> e : mergedItems.entrySet()) {
                WarehouseEntry entry = new WarehouseEntry(e.getKey().toStack(), e.getValue());
                if (pinnedItems.getOrDefault(e.getKey(), false))
                    entry.setPinned(true);
                // 设置正确的更新时间
                Long lastUpdated = lastUpdatedMap.get(e.getKey());
                if (lastUpdated != null && lastUpdated > 0) {
                    entry.setLastUpdated(lastUpdated);
                }
                baseCache.add(entry);
            }
            // 将聚合后的流体转换为 WarehouseEntry
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
                            net.minecraft.nbt.CompoundTag infiniteTag = new net.minecraft.nbt.CompoundTag();
                            infiniteTag.putBoolean(WarehouseConstants.INFINITE_TAG, true);
                            fluidStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                    net.minecraft.world.item.component.CustomData.of(infiniteTag));
                        }
                        baseCache.add(new WarehouseEntry(fluidStack, bucketCount));
                    }
                }
            }

            // 经验系统 (如果组内有人开启了经验升级)
            boolean hasExperienceUpgrade = group.stream()
                    .anyMatch(pw -> !pw.getUpgrade(com.portablestorage.upgrade.ExperienceUpgrade.ID).isEmpty());
            if (hasExperienceUpgrade) {
                ItemStack xpStack = new ItemStack(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE);
                List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
                lore.add(net.minecraft.network.chat.Component
                        .translatable("tooltip.portablestorage.experience.stored", mergedExperience)
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                lore.add(net.minecraft.network.chat.Component.literal(" "));
                // 获取当前仓库的升级阶数与等级维持状态
                ItemStack upgradeStack = getUpgrade(com.portablestorage.upgrade.ExperienceUpgrade.ID);
                int step = upgradeStack.isEmpty() ? 0
                        : com.portablestorage.upgrade.ExperienceUpgrade.getStep(upgradeStack);
                boolean maintain = !upgradeStack.isEmpty()
                        && com.portablestorage.upgrade.ExperienceUpgrade.isMaintaining(upgradeStack);

                net.minecraft.ChatFormatting hintColor = maintain ? net.minecraft.ChatFormatting.RED
                        : net.minecraft.ChatFormatting.GRAY;

                lore.add(net.minecraft.network.chat.Component
                        .translatable("tooltip.portablestorage.experience.withdraw", step)
                        .withStyle(hintColor));
                lore.add(net.minecraft.network.chat.Component
                        .translatable("tooltip.portablestorage.experience.deposit", step)
                        .withStyle(hintColor));
                lore.add(
                        net.minecraft.network.chat.Component.translatable("tooltip.portablestorage.experience.exchange")
                                .withStyle(hintColor));

                if (maintain) {
                    lore.add(net.minecraft.network.chat.Component
                            .translatable("tooltip.portablestorage.experience.maintain_blocked")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                }

                xpStack.set(net.minecraft.core.component.DataComponents.LORE,
                        new net.minecraft.world.item.component.ItemLore(lore));
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
                if (startExact)
                    tempQuery = tempQuery.substring(1);
                if (endExact && tempQuery.length() > 0)
                    tempQuery = tempQuery.substring(0, tempQuery.length() - 1);

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
        if (checkMatch(name, finalQuery, startExact, endExact))
            return true;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
        if (checkMatch(id, finalQuery, startExact, endExact))
            return true;

        net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String lineText = line.getString().toLowerCase();
                if (checkMatch(lineText, finalQuery, startExact, endExact))
                    return true;
            }
        }
        return false;
    }

    private boolean checkMatch(String target, String query, boolean startExact, boolean endExact) {
        if (query.isEmpty())
            return true;
        if (startExact && endExact)
            return target.equals(query);
        if (startExact)
            return target.startsWith(query);
        if (endExact)
            return target.endsWith(query);
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
                    if (e.isPinned())
                        pinned = true;
                }

                ItemStack displayStack = new ItemStack(group.getKey());
                displayStack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                net.minecraft.nbt.CompoundTag collapseTag = new net.minecraft.nbt.CompoundTag();
                collapseTag.putBoolean(com.portablestorage.util.WarehouseConstants.SMART_COLLAPSE_TAG, true);
                displayStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(collapseTag));

                WarehouseEntry collapsedEntry = new WarehouseEntry(displayStack, totalCount);
                collapsedEntry.setPinned(pinned);
                // 设置正确的更新时间
                if (lastUpdated > 0) {
                    collapsedEntry.setLastUpdated(lastUpdated);
                }
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
            case 1 ->
                Comparator.comparing(e -> e.getItemStack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
            case 2 -> Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
            case 3 -> Comparator.comparingLong(WarehouseEntry::getLastUpdated);
            default -> (a, b) -> 0;
        };

        if (!isAscending)
            comparator = comparator.reversed();

        // 置顶逻辑：置顶项排在最前面
        Comparator<WarehouseEntry> finalComparator = Comparator
                .<WarehouseEntry, Boolean>comparing(WarehouseEntry::isPinned).reversed()
                .thenComparing(comparator)
                .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));

        list.sort(finalComparator);
    }

    private net.minecraft.world.item.Item getVirtualItemForFluid(FluidVariant fluid) {
        if (fluid.isOf(Fluids.LAVA))
            return com.portablestorage.item.ModItems.VIRTUAL_LAVA;
        if (fluid.isOf(Fluids.WATER))
            return com.portablestorage.item.ModItems.VIRTUAL_WATER;
        return null;
    }

    // ========== 持久化逻辑 ==========

    public void readNbt(CompoundTag tag, HolderLookup.Provider registries) {
        storage.clear();
        java.util.Optional<ListTag> listOpt = tag.getList("storage");
        ListTag list = listOpt.orElseGet(ListTag::new);
        for (int i = 0; i < list.size(); i++) {
            java.util.Optional<CompoundTag> entryTagOpt = list.getCompound(i);
            entryTagOpt.ifPresent(entryTag -> storage.add(WarehouseEntry.fromNbt(entryTag, registries)));
        }

        fluidStorage.clear();
        if (tag.contains("fluids")) {
            java.util.Optional<ListTag> fluidListOpt = tag.getList("fluids");
            if (fluidListOpt.isEmpty())
                return;
            ListTag fluidList = fluidListOpt.get();
            for (int i = 0; i < fluidList.size(); i++) {
                java.util.Optional<CompoundTag> fluidTagOpt = fluidList.getCompound(i);
                if (fluidTagOpt.isEmpty())
                    continue;
                CompoundTag fluidTag = fluidTagOpt.get();
                Identifier id = Identifier.tryParse(fluidTag.getString("fluid").orElse(""));
                if (id == null)
                    continue;
                java.util.Optional<net.minecraft.core.Holder.Reference<Fluid>> fluidHolderOpt = BuiltInRegistries.FLUID
                        .get(id);
                if (fluidHolderOpt.isEmpty())
                    continue;
                Fluid fluid = fluidHolderOpt.get().value();

                net.minecraft.core.component.DataComponentPatch patch = net.minecraft.core.component.DataComponentPatch.EMPTY;
                if (fluidTag.contains("components")) {
                    java.util.Optional<CompoundTag> componentsOpt = fluidTag.getCompound("components");
                    if (componentsOpt.isPresent()) {
                        patch = net.minecraft.core.component.DataComponentPatch.CODEC
                                .parse(net.minecraft.nbt.NbtOps.INSTANCE, componentsOpt.get())
                                .getOrThrow();
                    }
                }

                long amount = fluidTag.getLong("amount").orElse(0L);
                FluidVariant variant = FluidVariant.of(fluid, patch);
                fluidStorage.put(variant, amount);
            }
        }

        this.visibleRows = tag.contains("visibleRows") ? tag.getInt("visibleRows").orElse(6) : 6;
        this.isFolded = tag.contains("isFolded") ? tag.getBoolean("isFolded").orElse(true) : true;
        this.sortMode = tag.getInt("sortMode").orElse(0);
        this.isAscending = tag.getBoolean("isAscending").orElse(false);
        this.quickInteraction = tag.contains("quickInteraction")
                ? tag.getBoolean("quickInteraction").orElse(true)
                : true;
        this.smartCollapse = tag.getBoolean("smartCollapse").orElse(false);
        boolean craftRefillFlag = tag.getBoolean("craftRefill").orElse(true);
        this.craftRefill = !tag.contains("craftRefill") || craftRefillFlag;
        boolean enabledFlag = tag.getBoolean("enabled").orElse(true);
        this.enabled = !tag.contains("enabled") || enabledFlag;
        int activationType = tag.getInt("activationType").orElse(WarehouseType.NONE.ordinal());
        this.type = WarehouseType.values()[activationType];
        this.experience = tag.getLong("experience").orElse(0L);
        tag.getString("ownerName").ifPresent(name -> this.ownerName = name);

        // 升级系统
        upgradeStorage.clear();
        if (tag.contains("upgrades")) {
            java.util.Optional<ListTag> upgradeListOpt = tag.getList("upgrades");
            if (upgradeListOpt.isPresent()) {
                ListTag upgradeList = upgradeListOpt.get();
                for (int i = 0; i < upgradeList.size(); i++) {
                    java.util.Optional<CompoundTag> uTagOpt = upgradeList.getCompound(i);
                    if (uTagOpt.isEmpty())
                        continue;
                    CompoundTag uTag = uTagOpt.get();

                    String idStr = uTag.getString("id").orElse("");
                    Identifier id = Identifier.tryParse(idStr);
                    if (id == null)
                        continue;

                    java.util.Optional<CompoundTag> itemTagOpt = uTag.getCompound("item");
                    if (itemTagOpt.isEmpty())
                        continue;
                    ItemStack stack = WarehouseEntry.itemFromNbt(itemTagOpt.get(), registries);
                    if (!stack.isEmpty()) {
                        upgradeStorage.put(id, stack);
                    }
                }
            }
        }
        this.upgradeScrollOffset = tag.getInt("upgradeScrollOffset").orElse(0);

        this.hopperFilters.clear();
        this.hopperFilterBlacklist = tag.getBoolean("hopperFilterBlacklist").orElse(true);
        if (tag.contains("hopperFilters")) {
            java.util.Optional<ListTag> filterListOpt = tag.getList("hopperFilters");
            if (filterListOpt.isPresent()) {
                ListTag filterList = filterListOpt.get();
                for (int i = 0; i < filterList.size(); i++) {
                    this.hopperFilters.add(filterList.getString(i).orElse(""));
                }
            }
        } else {
            // 默认黑名单
            this.hopperFilterBlacklist = true;
        }

        this.foodFilters.clear();
        this.foodFilterBlacklist = tag.getBoolean("foodFilterBlacklist").orElse(true);
        if (tag.contains("foodFilters")) {
            java.util.Optional<ListTag> filterListOpt = tag.getList("foodFilters");
            if (filterListOpt.isPresent()) {
                ListTag filterList = filterListOpt.get();
                for (int i = 0; i < filterList.size(); i++) {
                    this.foodFilters.add(filterList.getString(i).orElse(""));
                }
            }
        } else {
            // 默认黑名单
            this.foodFilterBlacklist = true;
        }

        this.forbiddenPlayers.clear();
        if (tag.contains("forbiddenPlayers")) {
            java.util.Optional<ListTag> forbiddenListOpt = tag.getList("forbiddenPlayers");
            if (forbiddenListOpt.isPresent()) {
                ListTag forbiddenList = forbiddenListOpt.get();
                for (int i = 0; i < forbiddenList.size(); i++) {
                    java.util.Optional<String> uuidStrOpt = forbiddenList.getString(i);
                    if (uuidStrOpt.isEmpty())
                        continue;
                    try {
                        java.util.UUID uuid = java.util.UUID.fromString(uuidStrOpt.get());
                        this.forbiddenPlayers.add(uuid);
                    } catch (IllegalArgumentException ignored) {
                        // 无效 UUID，忽略该条目
                    }
                }
            }
        }

        // 裂隙升级数据：使用显式字段而不是 NbtUtils.readBlockPos/UUID
        if (tag.contains("riftReturnDim")) {
            String dimStr = tag.getString("riftReturnDim").orElse("");
            this.riftReturnDim = dimStr.isEmpty() ? null : Identifier.tryParse(dimStr);

            if (tag.contains("riftReturnPosX") && tag.contains("riftReturnPosY") && tag.contains("riftReturnPosZ")) {
                int x = tag.getInt("riftReturnPosX").orElse(0);
                int y = tag.getInt("riftReturnPosY").orElse(0);
                int z = tag.getInt("riftReturnPosZ").orElse(0);
                this.riftReturnPos = new net.minecraft.core.BlockPos(x, y, z);
            } else {
                this.riftReturnPos = null;
            }
            this.riftReturnYaw = tag.getFloat("riftReturnYaw").orElse(0.0F);
            this.riftReturnPitch = tag.getFloat("riftReturnPitch").orElse(0.0F);
        } else {
            this.riftReturnDim = null;
            this.riftReturnPos = null;
        }
        this.riftPlotX = tag.getInt("riftPlotX").orElse(Integer.MIN_VALUE);
        this.riftPlotZ = tag.getInt("riftPlotZ").orElse(Integer.MIN_VALUE);
        this.riftInitialized = tag.getBoolean("riftInitialized").orElse(false);

        if (tag.contains("riftLastPosX") && tag.contains("riftLastPosY") && tag.contains("riftLastPosZ")) {
            int lx = tag.getInt("riftLastPosX").orElse(0);
            int ly = tag.getInt("riftLastPosY").orElse(0);
            int lz = tag.getInt("riftLastPosZ").orElse(0);
            this.riftLastPos = new net.minecraft.core.BlockPos(lx, ly, lz);
        } else {
            this.riftLastPos = null;
        }
        this.riftLastYaw = tag.getFloat("riftLastYaw").orElse(0.0F);
        this.riftLastPitch = tag.getFloat("riftLastPitch").orElse(0.0F);

        tag.getString("avatarUuid").ifPresent(uuidStr -> {
            try {
                this.avatarUuid = java.util.UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                this.avatarUuid = null;
            }
        });

        this.markDirty();
    }

    public void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarehouseEntry entry : storage)
            list.add(entry.toNbt(registries));
        tag.put("storage", list);

        ListTag fluidList = new ListTag();
        for (Map.Entry<FluidVariant, Long> entry : fluidStorage.entrySet()) {
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString("fluid", BuiltInRegistries.FLUID.getKey(entry.getKey().getFluid()).toString());
            net.minecraft.core.component.DataComponentPatch patch = entry.getKey().getComponents();
            if (!patch.isEmpty()) {
                fluidTag.put("components",
                        net.minecraft.core.component.DataComponentPatch.CODEC
                                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, patch)
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
        tag.putString("ownerName", ownerName);

        // 升级系统
        ListTag upgradeList = new ListTag();
        for (Map.Entry<Identifier, ItemStack> entry : upgradeStorage.entrySet()) {
            CompoundTag uTag = new CompoundTag();
            uTag.putString("id", entry.getKey().toString());
            CompoundTag itemTag = WarehouseEntry.itemToNbt(entry.getValue(), registries);
            uTag.put("item", itemTag);
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

        // forbiddenPlayers：持久化为 UUID 字符串列表
        ListTag forbiddenList = new ListTag();
        for (UUID uuid : forbiddenPlayers) {
            forbiddenList.add(net.minecraft.nbt.StringTag.valueOf(uuid.toString()));
        }
        tag.put("forbiddenPlayers", forbiddenList);

        // 裂隙升级数据
        if (riftReturnDim != null) {
            tag.putString("riftReturnDim", riftReturnDim.toString());
            if (riftReturnPos != null) {
                tag.putInt("riftReturnPosX", riftReturnPos.getX());
                tag.putInt("riftReturnPosY", riftReturnPos.getY());
                tag.putInt("riftReturnPosZ", riftReturnPos.getZ());
            }
            tag.putFloat("riftReturnYaw", riftReturnYaw);
            tag.putFloat("riftReturnPitch", riftReturnPitch);
        }
        tag.putInt("riftPlotX", riftPlotX);
        tag.putInt("riftPlotZ", riftPlotZ);
        tag.putBoolean("riftInitialized", riftInitialized);
        if (riftLastPos != null) {
            tag.putInt("riftLastPosX", riftLastPos.getX());
            tag.putInt("riftLastPosY", riftLastPos.getY());
            tag.putInt("riftLastPosZ", riftLastPos.getZ());
            tag.putFloat("riftLastYaw", riftLastYaw);
            tag.putFloat("riftLastPitch", riftLastPitch);
        }
        if (avatarUuid != null) {
            tag.putString("avatarUuid", avatarUuid.toString());
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
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            WarehouseEntryKey that = (WarehouseEntryKey) o;
            return item == that.item && Objects.equals(patch, that.patch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, patch);
        }
    }
}
