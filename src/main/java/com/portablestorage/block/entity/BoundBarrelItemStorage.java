package com.portablestorage.block.entity;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.logic.WarehouseManager;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

public class BoundBarrelItemStorage implements Storage<ItemVariant> {
    private final PlayerWarehouse warehouse;
    private final SimpleContainer filters;

    public BoundBarrelItemStorage(PlayerWarehouse warehouse, SimpleContainer filters) {
        this.warehouse = warehouse;
        this.filters = filters;
    }

    private boolean isAllowed(ItemVariant resource) {
        for (int i = 0; i < filters.getContainerSize(); i++) {
            ItemStack filterStack = filters.getItem(i);
            if (!filterStack.isEmpty()) {
                if (resource.matches(filterStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (!isAllowed(resource)) return 0;
        
        // 模拟 Transaction：仅在提交时修改
        transaction.addCloseCallback((context, result) -> {
            if (result.wasCommitted()) {
                ItemStack stack = resource.toStack((int) maxAmount);
                WarehouseManager.addItem(warehouse, stack);
            }
        });
        
        // 简单假设仓库能放下（通常仓库上限很高）
        // 实际上可以根据 warehouse.getMaxItemStackSize() 计算
        return maxAmount;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (!isAllowed(resource)) return 0;

        // 检查仓库中是否有足够的物品
        long count = 0;
        for (WarehouseEntry entry : warehouse.getStorageList()) {
            if (resource.matches(entry.getItemStack())) {
                count += entry.getCount();
            }
        }

        long toExtract = Math.min(count, maxAmount);
        if (toExtract <= 0) return 0;

        transaction.addCloseCallback((context, result) -> {
            if (result.wasCommitted()) {
                WarehouseManager.takeMatching(warehouse, resource.toStack(1), (int) toExtract, true);
            }
        });

        return toExtract;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        for (WarehouseEntry entry : warehouse.getStorageList()) {
            ItemVariant variant = ItemVariant.of(entry.getItemStack());
            if (isAllowed(variant)) {
                views.add(new WarehouseItemStorageView(warehouse, entry));
            }
        }
        return views.iterator();
    }

    private static class WarehouseItemStorageView implements StorageView<ItemVariant> {
        private final PlayerWarehouse warehouse;
        private final WarehouseEntry entry;

        public WarehouseItemStorageView(PlayerWarehouse warehouse, WarehouseEntry entry) {
            this.warehouse = warehouse;
            this.entry = entry;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (!resource.matches(entry.getItemStack())) return 0;
            long toExtract = Math.min(entry.getCount(), maxAmount);
            
            transaction.addCloseCallback((context, result) -> {
                if (result.wasCommitted()) {
                    WarehouseManager.takeMatching(warehouse, resource.toStack(1), (int) toExtract, true);
                }
            });
            return toExtract;
        }

        @Override
        public boolean isResourceBlank() {
            return entry.getItemStack().isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(entry.getItemStack());
        }

        @Override
        public long getAmount() {
            return entry.getCount();
        }

        @Override
        public long getCapacity() {
            return warehouse.getMaxItemStackSize();
        }
    }
}

