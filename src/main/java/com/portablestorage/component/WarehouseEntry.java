package com.portablestorage.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 仓库条目
 * v1 迁移兼容结构：仅用于旧存档读取和 UI 兼容视图，不应作为新写入主存模型。
 */
public class WarehouseEntry {
    /** 物品堆叠（用于存储物品类型和 NBT） */
    private final ItemStack itemStack;
    /** 物品数量 */
    private long count;
    /** 最后更新时间（毫秒时间戳） */
    private long lastUpdated;
    /** 是否置顶 */
    private boolean pinned;

    /**
     * 构造函数
     * @param stack 物品堆叠
     * @param count 物品数量
     */
    public WarehouseEntry(ItemStack stack, long count) {
        this.itemStack = stack.copyWithCount(1);
        this.count = count;
        this.lastUpdated = System.currentTimeMillis();
        this.pinned = false;
    }

    /**
     * 获取物品堆叠
     * @return 物品堆叠
     */
    public ItemStack getItemStack() { return itemStack; }
    
    /**
     * 获取物品数量
     * @return 物品数量
     */
    public long getCount() { return count; }
    
    /**
     * 获取最后更新时间
     * @return 最后更新时间（毫秒时间戳）
     */
    public long getLastUpdated() { return lastUpdated; }
    
    /**
     * 是否置顶
     * @return 是否置顶
     */
    public boolean isPinned() { return pinned; }
    
    /**
     * 设置置顶状态
     * @param pinned 是否置顶
     */
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    
    /**
     * 设置最后更新时间
     * @param timestamp 时间戳（毫秒）
     */
    public void setLastUpdated(long timestamp) { this.lastUpdated = timestamp; }

    /**
     * 增加物品数量
     * @param amount 增加的数量
     */
    public void add(long amount) {
        this.count += amount;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * 减少物品数量
     * @param amount 减少的数量
     */
    public void subtract(long amount) {
        this.count -= amount;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * 检查物品是否匹配
     * 唯一标识逻辑：Item + NBT 相同则视为同一种物品
     * @param stack 待匹配的物品堆叠
     * @return 是否匹配
     */
    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(this.itemStack, stack);
    }

    /**
     * 序列化为 NBT
     * @param registries 注册表提供者
     * @return NBT 标签
     */
    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // 使用 ItemStack.CODEC + registries 序列化
        var ops = net.minecraft.nbt.NbtOps.INSTANCE;
        var ctx = registries.createSerializationContext(ops);
        net.minecraft.world.item.ItemStack.CODEC.encodeStart(ctx, itemStack)
                .resultOrPartial(__ -> {
                })
                .ifPresent(nbt -> tag.put("item", nbt));
        tag.putLong("count", count);
        tag.putLong("lastUpdated", lastUpdated);
        tag.putBoolean("pinned", pinned);
        return tag;
    }

    /**
     * 从 NBT 反序列化
     * @param tag NBT 标签
     * @param registries 注册表提供者
     * @return 仓库条目实例
     */
    public static WarehouseEntry fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        java.util.Optional<CompoundTag> itemTagOpt = tag.getCompound("item");
        ItemStack stack = itemFromNbt(itemTagOpt.orElse(new CompoundTag()), registries);
        long count = tag.getLong("count").orElse(0L);
        WarehouseEntry entry = new WarehouseEntry(stack, count);
        entry.lastUpdated = tag.getLong("lastUpdated").orElse(0L);
        entry.pinned = tag.getBoolean("pinned").orElse(false);
        return entry;
    }

    public static ItemStack itemFromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        // 使用 ItemStack.CODEC 进行反序列化，避免依赖已移除的 parseOptional
        var ops = net.minecraft.nbt.NbtOps.INSTANCE;
        var ctx = registries.createSerializationContext(ops);
        return net.minecraft.world.item.ItemStack.CODEC.parse(ctx, tag)
                .resultOrPartial(__ -> {
                })
                .orElse(ItemStack.EMPTY);
    }

    public static CompoundTag itemToNbt(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        var ops = net.minecraft.nbt.NbtOps.INSTANCE;
        var ctx = registries.createSerializationContext(ops);
        net.minecraft.world.item.ItemStack.CODEC.encodeStart(ctx, stack)
                .resultOrPartial(__ -> {
                })
                .ifPresent(nbt -> {
                    if (nbt instanceof CompoundTag compound) {
                        tag.merge(compound);
                    } else {
                        tag.put("stack", nbt);
                    }
                });
        return tag;
    }
}

