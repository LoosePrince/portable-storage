package com.portablestorage.logic;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.config.ModConfig;
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
import net.minecraft.world.item.component.CustomData;
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
        if (stack.isEmpty()) return;

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
            addItemInternal(warehouse, stack);
            return stack;
        }

        net.minecraft.world.item.Item virtualItem = getVirtualFluidForItem(stack.getItem());
        if (virtualItem == null) {
            addItemInternal(warehouse, stack);
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
            // 牛奶等非 Fluid 类型的虚拟流体
            int originalCount = stack.getCount();
            ItemStack virtualStack = new ItemStack(virtualItem, originalCount);
            addItemInternal(warehouse, virtualStack);

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
        long limit = ModConfig.maxItemStackSize;
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
            int typeLimit = ModConfig.maxStorageTypes;
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

        // 特殊逻辑：流体提取
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

        long realCount = warehouse.getRealCount(slotIndex);
        int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
        ItemStack resultStack = stackInSlot.copyWithCount(toTake);

        // 动态查找玩家背包范围 (仅限 Main + Hotbar，排除装备和副手)
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
            if (movedCount > 0) {
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

            CustomData customData = entry.getItemStack().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            boolean isCollapsed = customData != null && customData.copyTag().getBoolean(WarehouseConstants.SMART_COLLAPSE_TAG);
            
            // 严禁提取折叠项（无论 force 与否，因为它是虚拟的）
            if (isCollapsed) {
                return ItemStack.EMPTY;
            }

            if (!force && isVirtualFluid(entry.getItemStack().getItem())) {
                return ItemStack.EMPTY;
            }

            FluidVariant fluid = getFluidForVirtualItem(entry.getItemStack().getItem());
            if (fluid != null) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long toExtract = (long) amount * FluidConstants.BUCKET;
                    long extracted = warehouse.extract(fluid, toExtract, transaction);
                    transaction.commit();

                    int bucketsExtracted = (int) (extracted / FluidConstants.BUCKET);
                    if (bucketsExtracted > 0) {
                        return entry.getItemStack().copyWithCount(bucketsExtracted);
                    }
                }
                return ItemStack.EMPTY;
            }

            long toRemove = Math.min(amount, entry.getCount());
            
            // 校验物品堆叠上限（除非是强制模式）
            if (!force) {
                toRemove = Math.min(toRemove, entry.getItemStack().getMaxStackSize());
            }

            ItemStack result = entry.getItemStack().copyWithCount((int) toRemove);
            entry.subtract(toRemove);
            if (entry.getCount() <= 0) warehouse.getStorageList().remove(entry);
            warehouse.markDirty();
            return result;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 补货逻辑用的匹配提取
     */
    public static ItemStack takeMatching(PlayerWarehouse warehouse, ItemStack template, int amount, boolean matchComponents) {
        int totalTaken = 0;
        ItemStack result = template.copyWithCount(0);
        List<WarehouseEntry> storage = warehouse.getStorageList();

        for (int i = storage.size() - 1; i >= 0; i--) {
            WarehouseEntry entry = storage.get(i);
            boolean matches = matchComponents ? entry.matches(template) : entry.getItemStack().is(template.getItem());

            if (matches) {
                long canTake = Math.min(amount - totalTaken, entry.getCount());
                if (canTake > 0) {
                    entry.subtract(canTake);
                    totalTaken += (int) canTake;
                    if (entry.getCount() <= 0) storage.remove(i);
                }
            }
            if (totalTaken >= amount) break;
        }

        if (totalTaken > 0) {
            result.setCount(totalTaken);
            warehouse.markDirty();
        }
        return result;
    }

    // 辅助方法

    private static net.minecraft.world.item.Item getVirtualFluidForItem(net.minecraft.world.item.Item item) {
        if (item == Items.LAVA_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_LAVA;
        if (item == Items.WATER_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_WATER;
        if (item == Items.MILK_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_MILK;
        return null;
    }

    private static boolean isVirtualFluid(net.minecraft.world.item.Item item) {
        return item == com.portablestorage.item.ModItems.VIRTUAL_LAVA ||
                item == com.portablestorage.item.ModItems.VIRTUAL_WATER ||
                item == com.portablestorage.item.ModItems.VIRTUAL_MILK;
    }

    private static FluidVariant getFluidForVirtualItem(net.minecraft.world.item.Item item) {
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
}

