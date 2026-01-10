package com.portablestorage.logic;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.util.WarehouseConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.*;

/**
 * 仓库业务逻辑处理器
 * 将复杂的存取、转换、过滤逻辑从数据层 (PlayerWarehouse) 抽离出来
 */
public class WarehouseManager {

    /**
     * 处理物品存入逻辑
     */
    public static void addItem(PlayerWarehouse warehouse, ItemStack stack) {
        addItem(warehouse, stack, null);
    }

    /**
     * 处理物品存入逻辑（带Player参数用于获取registries）
     */
    public static void addItem(PlayerWarehouse warehouse, ItemStack stack, Player player) {
        if (stack.isEmpty()) return;

        // 检查物品 NBT 大小限制
        if (player != null && !checkItemNbtSize(stack, player)) {
            return; // 超过大小限制，拒绝存入
        }

        // 尝试作为流体处理
        FluidVariant fluid = getFluidForVirtualItem(stack.getItem());
        if (fluid != null) {
            try (Transaction transaction = Transaction.openOuter()) {
                long toInsert = (long) stack.getCount() * FluidConstants.BUCKET;
                long inserted = warehouse.insert(fluid, toInsert, transaction);
                transaction.commit();

                int bucketsStored = (int) (inserted / FluidConstants.BUCKET);
                stack.shrink(bucketsStored);
            }
            if (stack.isEmpty()) return;
        }

        addItemInternal(warehouse, stack);
    }

    /**
     * 处理流体桶存入逻辑（带转换）
     */
    public static ItemStack addFluid(PlayerWarehouse warehouse, ItemStack stack, Player player) {
        if (!warehouse.isEnabled() || !warehouse.isQuickInteraction()) {
            addItem(warehouse, stack, player);
            return stack;
        }

        net.minecraft.world.item.Item virtualItem = getVirtualFluidForItem(stack.getItem());
        if (virtualItem == null) {
            addItem(warehouse, stack, player);
            return stack;
        }

        FluidVariant fluid = getFluidForVirtualItem(virtualItem);
        if (fluid != null) {
            int originalCount = stack.getCount();
            long toInsert = (long) originalCount * FluidConstants.BUCKET;

            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = warehouse.insert(fluid, toInsert, transaction);
                transaction.commit();

                int bucketsStored = (int) (inserted / FluidConstants.BUCKET);
                if (bucketsStored > 0) {
                    ItemStack emptyBuckets = new ItemStack(Items.BUCKET, bucketsStored);
                    stack.shrink(bucketsStored);

                    if (stack.isEmpty()) {
                        return emptyBuckets;
                    } else {
                        if (!player.getInventory().add(emptyBuckets)) {
                            player.drop(emptyBuckets, false);
                        }
                        return stack;
                    }
                }
            }
        } else {
            // 处理牛奶等非 Fluid 类型的虚拟流体
            int originalCount = stack.getCount();
            ItemStack virtualStack = new ItemStack(virtualItem, originalCount);
            addItem(warehouse, virtualStack, player);

            int stored = originalCount - virtualStack.getCount();
            if (stored > 0) {
                ItemStack emptyBuckets = new ItemStack(Items.BUCKET, stored);
                stack.shrink(stored);
                if (stack.isEmpty()) return emptyBuckets;
                if (!player.getInventory().add(emptyBuckets)) player.drop(emptyBuckets, false);
                return stack;
            }
        }

        return stack;
    }

    /**
     * 内部物品存入实现（处理堆叠限制等）
     */
    private static void addItemInternal(PlayerWarehouse warehouse, ItemStack stack) {
        if (stack.isEmpty()) return;
        
        List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
        // 优先存入当前仓库，然后是组内其他仓库
        for (PlayerWarehouse pw : group) {
            addItemToSingleWarehouse(pw, stack);
            if (stack.isEmpty()) break;
        }
    }

    private static void addItemToSingleWarehouse(PlayerWarehouse warehouse, ItemStack stack) {
        if (stack.isEmpty()) return;
        long limit = warehouse.getMaxItemStackSize();
        boolean changed = false;

        List<WarehouseEntry> storage = warehouse.getStorageList();
        WarehouseEntry existingEntry = null;
        for (WarehouseEntry entry : storage) {
            if (entry.matches(stack)) {
                existingEntry = entry;
                break;
            }
        }

        if (existingEntry != null) {
            if (limit > 0) {
                long current = existingEntry.getCount();
                long canAdd = Math.max(0, limit - current);
                if (canAdd > 0) {
                    int toAdd = (int) Math.min(stack.getCount(), canAdd);
                    existingEntry.add(toAdd);
                    stack.shrink(toAdd);
                    changed = true;
                }
            } else {
                existingEntry.add(stack.getCount());
                stack.setCount(0);
                changed = true;
            }
        } else {
            int typeLimit = warehouse.getMaxStorageTypes();
            if (typeLimit < 0 || storage.size() < typeLimit) {
                if (limit > 0) {
                    int toAdd = (int) Math.min(stack.getCount(), limit);
                    if (toAdd > 0) {
                        storage.add(new WarehouseEntry(stack.copyWithCount(toAdd), toAdd));
                        stack.shrink(toAdd);
                        changed = true;
                    }
                } else {
                    storage.add(new WarehouseEntry(stack.copy(), stack.getCount()));
                    stack.setCount(0);
                    changed = true;
                }
            }
        }

        if (changed) {
            warehouse.markDirty();
        }
    }

    /**
     * 尝试将仓库物品转移到玩家背包
     */
    public static void tryTransferToInventory(PlayerWarehouse warehouse, int slotIndex, Player player) {
        if (!warehouse.isEnabled() || !warehouse.isQuickInteraction()) return;

        ItemStack stackInSlot = warehouse.getItem(slotIndex);
        if (stackInSlot.isEmpty()) return;

        // 流体提取逻辑
        if (isVirtualFluid(stackInSlot.getItem())) {
            int emptyBucketSlot = findEmptyBucket(player);
            if (emptyBucketSlot != -1) {
                ItemStack fluidBucket = getFluidBucket(stackInSlot.getItem());
                if (!fluidBucket.isEmpty()) {
                    Slot slot = player.containerMenu.getSlot(emptyBucketSlot);
                    ItemStack bucketStack = slot.getItem();

                    bucketStack.shrink(1);
                    if (bucketStack.isEmpty()) slot.set(ItemStack.EMPTY);

                    if (!player.getInventory().add(fluidBucket)) {
                        player.drop(fluidBucket, false);
                    }

                    removeItem(warehouse, slotIndex, 1, true);
                    player.containerMenu.broadcastChanges();
                }
            }
            return;
        }

        // 经验提取逻辑（Shift 点击尝试灌满背包中所有玻璃瓶）
        if (stackInSlot.is(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE)) {
            handleExperienceQuickTransfer(warehouse, player);
            return;
        }

        long realCount = warehouse.getRealCount(slotIndex);
        if (realCount <= 0) {
            return; // 确保有物品可提取
        }
        int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
        ItemStack resultStack = stackInSlot.copyWithCount(toTake);

        // 查找玩家背包范围（仅限主背包和快捷栏，排除装备和副手）
        int inventoryStart = -1;
        int inventoryEnd = -1;
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            Slot slot = player.containerMenu.slots.get(i);
            if (slot.container instanceof Inventory) {
                int containerSlot = slot.getContainerSlot();
                if (containerSlot >= 0 && containerSlot < 36) {
                    if (inventoryStart == -1) inventoryStart = i;
                    inventoryEnd = i + 1;
                }
            }
        }

        if (inventoryStart != -1 && ((AbstractContainerMenuAccessor) player.containerMenu).invokeMoveItemStackTo(resultStack,
                inventoryStart, inventoryEnd, true)) {
            int movedCount = toTake - resultStack.getCount();
            // 确保只移除实际移动的数量，防止刷物品
            if (movedCount > 0 && movedCount <= toTake) {
                removeItem(warehouse, slotIndex, movedCount, true);
            }
        }
    }

    /**
     * 移除物品逻辑
     */
    public static ItemStack removeItem(PlayerWarehouse warehouse, int slot, int amount, boolean force) {
        List<WarehouseEntry> sorted = warehouse.getSortedEntries();
        int actualIndex = slot + (warehouse.getScrollOffset() * 9);
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            WarehouseEntry entry = sorted.get(actualIndex);
            ItemStack itemType = entry.getItemStack();

            net.minecraft.nbt.CompoundTag tag = itemType.getTag();
            boolean isCollapsed = tag != null && tag.getBoolean(WarehouseConstants.SMART_COLLAPSE_TAG);
            
            // 严禁提取折叠项
            if (isCollapsed) {
                return ItemStack.EMPTY;
            }

            // 严禁提取经验项 (虚拟物品)
            if (itemType.is(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE)) {
                return ItemStack.EMPTY;
            }

            if (!force && isVirtualFluid(itemType.getItem())) {
                return ItemStack.EMPTY;
            }

            // 流体提取逻辑
            FluidVariant fluid = getFluidForVirtualItem(itemType.getItem());
            if (fluid != null) {
                long totalExtracted = 0;
                List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
                // 优先从当前仓库提取
                for (PlayerWarehouse pw : group) {
                    try (Transaction transaction = Transaction.openOuter()) {
                        long toExtract = (long) amount * FluidConstants.BUCKET - totalExtracted;
                        if (toExtract <= 0) break;
                        long extracted = pw.extract(fluid, toExtract, transaction);
                        transaction.commit();
                        totalExtracted += extracted;
                    }
                }
                
                int bucketsExtracted = (int) (totalExtracted / FluidConstants.BUCKET);
                if (bucketsExtracted > 0) {
                    return itemType.copyWithCount(bucketsExtracted);
                }
                return ItemStack.EMPTY;
            }

            // 物品提取逻辑
            long toRemoveTotal = Math.min(amount, entry.getCount());
            if (!force) {
                toRemoveTotal = Math.min(toRemoveTotal, itemType.getMaxStackSize());
            }

            long remainingToRemove = toRemoveTotal;
            List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
            
            // 优先从当前仓库提取
            for (PlayerWarehouse pw : group) {
                if (remainingToRemove <= 0) break;
                remainingToRemove -= removeFromSingleWarehouse(pw, itemType, (int) remainingToRemove);
            }

            if (remainingToRemove < toRemoveTotal) {
                warehouse.markDirty();
                return itemType.copyWithCount((int) (toRemoveTotal - remainingToRemove));
            }
        }
        return ItemStack.EMPTY;
    }

    private static int removeFromSingleWarehouse(PlayerWarehouse warehouse, ItemStack template, int amount) {
        int removed = 0;
        List<WarehouseEntry> storage = warehouse.getStorageList();
        for (int i = storage.size() - 1; i >= 0; i--) {
            WarehouseEntry entry = storage.get(i);
            if (entry.matches(template)) {
                long canTake = Math.min(amount - removed, entry.getCount());
                if (canTake > 0) {
                    entry.subtract(canTake);
                    removed += (int) canTake;
                    if (entry.getCount() <= 0) storage.remove(i);
                    warehouse.markDirty();
                }
            }
            if (removed >= amount) break;
        }
        return removed;
    }

    /**
     * 补货逻辑用的匹配提取
     */
    public static ItemStack takeMatching(PlayerWarehouse warehouse, ItemStack template, int amount, boolean matchComponents) {
        int totalTaken = 0;
        ItemStack result = template.copyWithCount(0);
        
        List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
        // 优先从当前仓库提取
        for (PlayerWarehouse pw : group) {
            if (totalTaken >= amount) break;
            
            List<WarehouseEntry> storage = pw.getStorageList();
            for (int i = storage.size() - 1; i >= 0; i--) {
                WarehouseEntry entry = storage.get(i);
                boolean matches = matchComponents ? entry.matches(template) : entry.getItemStack().is(template.getItem());

                if (matches) {
                    long canTake = Math.min(amount - totalTaken, entry.getCount());
                    if (canTake > 0) {
                        entry.subtract(canTake);
                        totalTaken += (int) canTake;
                        if (entry.getCount() <= 0) storage.remove(i);
                        pw.markDirty();
                    }
                }
                if (totalTaken >= amount) break;
            }
        }

        if (totalTaken > 0) {
            result.setCount(totalTaken);
            warehouse.markDirty();
        }
        return result;
    }

    // 辅助方法

    private static void handleExperienceQuickTransfer(PlayerWarehouse warehouse, Player player) {
        long availableXp = 0;
        List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
        for (PlayerWarehouse pw : group) {
            availableXp += pw.getExperience();
        }
        
        if (availableXp < 11) return; // 11 XP per bottle

        int totalBottlesFilled = 0;
        // 扫描玩家整个背包找玻璃瓶
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            Slot slot = player.containerMenu.slots.get(i);
            if (slot.container instanceof Inventory) {
                int containerSlot = slot.getContainerSlot();
                if (containerSlot >= 0 && containerSlot < 36) { // 仅限主背包和快捷栏
                    ItemStack stack = slot.getItem();
                    if (stack.is(Items.GLASS_BOTTLE)) {
                        int canFill = (int) (availableXp / 11);
                        int toFill = Math.min(stack.getCount(), canFill);
                        
                        if (toFill > 0) {
                            stack.shrink(toFill);
                            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
                            
                            ItemStack expBottles = new ItemStack(Items.EXPERIENCE_BOTTLE, toFill);
                            if (!player.getInventory().add(expBottles)) {
                                player.drop(expBottles, false);
                            }
                            
                            long usedXp = (long) toFill * 11;
                            availableXp -= usedXp;
                            
                            // 从组内扣除经验
                            long remainingToRemove = usedXp;
                            for (PlayerWarehouse pw : group) {
                                long fromThis = Math.min(pw.getExperience(), remainingToRemove);
                                if (fromThis > 0) {
                                    pw.addExperience(-fromThis);
                                    remainingToRemove -= fromThis;
                                }
                                if (remainingToRemove <= 0) break;
                            }
                            
                            totalBottlesFilled += toFill;
                            slot.setChanged();
                        }
                    }
                }
            }
            if (availableXp < 11) break;
        }
        
        if (totalBottlesFilled > 0) {
            warehouse.markDirty();
            player.containerMenu.broadcastChanges();
        }
    }

    private static net.minecraft.world.item.Item getVirtualFluidForItem(net.minecraft.world.item.Item item) {
        if (item == Items.LAVA_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_LAVA;
        if (item == Items.WATER_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_WATER;
        if (item == Items.MILK_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_MILK;
        return null;
    }

    public static boolean isVirtualFluid(net.minecraft.world.item.Item item) {
        return item == com.portablestorage.item.ModItems.VIRTUAL_LAVA ||
                item == com.portablestorage.item.ModItems.VIRTUAL_WATER ||
                item == com.portablestorage.item.ModItems.VIRTUAL_MILK;
    }

    public static FluidVariant getFluidForVirtualItem(net.minecraft.world.item.Item item) {
        if (item == com.portablestorage.item.ModItems.VIRTUAL_LAVA) return FluidVariant.of(Fluids.LAVA);
        if (item == com.portablestorage.item.ModItems.VIRTUAL_WATER) return FluidVariant.of(Fluids.WATER);
        return null;
    }

    private static ItemStack getFluidBucket(net.minecraft.world.item.Item virtualFluid) {
        if (virtualFluid == com.portablestorage.item.ModItems.VIRTUAL_LAVA) return new ItemStack(Items.LAVA_BUCKET);
        if (virtualFluid == com.portablestorage.item.ModItems.VIRTUAL_WATER) return new ItemStack(Items.WATER_BUCKET);
        if (virtualFluid == com.portablestorage.item.ModItems.VIRTUAL_MILK) return new ItemStack(Items.MILK_BUCKET);
        return ItemStack.EMPTY;
    }

    private static int findEmptyBucket(Player player) {
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            Slot slot = player.containerMenu.slots.get(i);
            // 仅搜索玩家主背包和快捷栏
            if (slot.container instanceof Inventory && slot.getContainerSlot() < 36 && slot.getItem().is(Items.BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 检查物品NBT数据大小是否超过限制
     * @param stack 要检查的物品
     * @param player 玩家对象，用于获取registries
     * @return true表示可以存入，false表示超过限制
     */
    private static boolean checkItemNbtSize(ItemStack stack, Player player) {
        int maxSize = com.portablestorage.config.ModConfig.maxItemNbtSize;
        if (maxSize < 0) {
            return true; // -1表示不限制
        }

        try {
            // 从玩家的level获取registries
            if (player == null || player.level() == null) {
                return true; // 无法获取registries，允许存入
            }
            
            net.minecraft.nbt.CompoundTag savedTag = new net.minecraft.nbt.CompoundTag();
            stack.save(savedTag);
            
            if (savedTag == null) {
                return true; // 没有NBT数据，允许存入
            }
            
            // 将NBT序列化为字节数组来计算大小
            // 使用 Tag 而不是 CompoundTag，因为 saveOptional 可能返回其他类型的 Tag
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream dos = new java.io.DataOutputStream(baos)) {
                net.minecraft.nbt.NbtIo.writeUnnamedTag(savedTag, dos);
            }
            int size = baos.size();
            
            return size <= maxSize;
        } catch (Exception e) {
            // 如果检查失败，允许存入（避免阻止正常物品）
            com.portablestorage.PortableStorage.LOGGER.warn("Failed to check item NBT size", e);
            return true;
        }
    }
}


