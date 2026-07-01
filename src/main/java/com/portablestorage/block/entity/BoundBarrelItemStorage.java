package com.portablestorage.block.entity;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.storage.service.WarehouseService;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.portablestorage.storage.key.WarehouseStackKey;

public class BoundBarrelItemStorage implements Storage<ItemVariant> {
    private final PlayerWarehouse warehouse;
    private final SimpleContainer filters;
    private final MinecraftServer server;
    private final UUID ownerUuid;

    public BoundBarrelItemStorage(PlayerWarehouse warehouse, SimpleContainer filters, MinecraftServer server, UUID ownerUuid) {
        this.warehouse = warehouse;
        this.filters = filters;
        this.server = server;
        this.ownerUuid = ownerUuid;
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
        if (!isAllowed(resource) || maxAmount <= 0) return 0;
        
        long insertable = calculateInsertableAmount(resource, maxAmount);
        if (insertable <= 0) return 0;

        Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> snapshot = snapshotSharedGroup();
        ItemStack stack = resource.toStack((int) insertable);
        warehouse.withSuppressedDirtyNotifications(() -> {
            WarehouseManager.addItem(warehouse, stack, null, "bound_barrel.insert");
            return null;
        });
        long inserted = insertable - stack.getCount();
        if (inserted <= 0) {
            restoreSharedGroup(snapshot);
            return 0;
        }
        
        transaction.addCloseCallback((context, result) -> {
            if (result.wasCommitted()) {
                commitOwnerWarehouse("bound_barrel.insert");
            } else {
                restoreSharedGroup(snapshot);
            }
        });
        
        return inserted;
    }
    
    /**
     * 计算理论上能插入的最大数量
     * @param resource 物品变体
     * @param maxAmount 请求的最大数量
     * @return 理论上能插入的数量
     */
    private long calculateInsertableAmount(ItemVariant resource, long maxAmount) {
        long limit = warehouse.getMaxItemStackSize();
        if (limit < 0) {
            // 无限制，返回请求数量
            return maxAmount;
        }
        
        // 检查共享组中该物品的当前数量
        long currentCount = 0;
        ItemStack template = resource.toStack(1);
        List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
        for (PlayerWarehouse pw : group) {
            currentCount += pw.getStoredItemAmount(template);
        }
        
        // 计算还能插入多少
        long canInsert = limit - currentCount;
        if (canInsert <= 0) {
            // 当前类型已满，检查是否还能添加新类型
            if (currentCount == 0) {
                // 该物品类型不存在，检查类型数量限制
                int typeLimit = warehouse.getMaxStorageTypes();
                if (typeLimit >= 0) {
                    // 检查共享组中是否还有空间添加新类型
                    int totalTypes = 0;
                    for (PlayerWarehouse pw : group) {
                        totalTypes = Math.max(totalTypes, pw.getStoredItemTypeCount());
                    }
                    if (totalTypes >= typeLimit) {
                        return 0; // 类型数量已达上限
                    }
                }
                // 可以添加新类型，但受单类型数量限制
                return Math.min(maxAmount, limit);
            }
            // 当前类型已满且已存在，无法插入
            return 0;
        }
        
        return Math.min(maxAmount, canInsert);
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (!isAllowed(resource) || maxAmount <= 0) return 0;

        long available = countAllowedResource(resource);
        long requested = Math.min(available, maxAmount);
        if (requested <= 0) return 0;

        Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> snapshot = snapshotSharedGroup();
        ItemStack extracted = warehouse.withSuppressedDirtyNotifications(() ->
                WarehouseManager.takeMatching(warehouse, resource.toStack(1), (int) requested, true));
        long extractedCount = extracted.getCount();
        if (extractedCount <= 0) {
            restoreSharedGroup(snapshot);
            return 0;
        }

        transaction.addCloseCallback((context, result) -> {
            if (result.wasCommitted()) {
                commitOwnerWarehouse("bound_barrel.extract");
            } else {
                restoreSharedGroup(snapshot);
            }
        });

        return extractedCount;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        List<StorageView<ItemVariant>> views = new ArrayList<>();
        List<PlayerWarehouse> group = warehouse.getSharedGroupWarehouses();
        
        // 聚合组内所有物品
        java.util.Map<ItemVariant, Long> merged = new java.util.LinkedHashMap<>();
        for (PlayerWarehouse pw : group) {
            for (WarehouseEntry entry : pw.getStorageList()) {
                ItemVariant variant = ItemVariant.of(entry.getItemStack());
                if (isAllowed(variant)) {
                    merged.put(variant, merged.getOrDefault(variant, 0L) + entry.getCount());
                }
            }
        }

        for (java.util.Map.Entry<ItemVariant, Long> e : merged.entrySet()) {
            views.add(new WarehouseItemStorageView(warehouse, e.getKey(), e.getValue(), reason -> commitOwnerWarehouse(reason)));
        }
        return views.iterator();
    }

    private void commitOwnerWarehouse(String reason) {
        if (server != null && ownerUuid != null) {
            WarehouseService.commit(server, ownerUuid, reason);
        }
    }

    private long countAllowedResource(ItemVariant resource) {
        long totalCount = 0;
        for (PlayerWarehouse pw : warehouse.getSharedGroupWarehouses()) {
            for (WarehouseEntry entry : pw.getStorageList()) {
                if (resource.matches(entry.getItemStack())) {
                    totalCount += entry.getCount();
                }
            }
        }
        return totalCount;
    }

    private Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> snapshotSharedGroup() {
        Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> snapshot = new LinkedHashMap<>();
        for (PlayerWarehouse pw : warehouse.getSharedGroupWarehouses()) {
            snapshot.put(pw, pw.unifiedStorageSnapshot());
        }
        return snapshot;
    }

    private static void restoreSharedGroup(Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> snapshot) {
        for (Map.Entry<PlayerWarehouse, Map<WarehouseStackKey, Long>> entry : snapshot.entrySet()) {
            entry.getKey().restoreUnifiedStorageSnapshot(entry.getValue());
        }
    }

    private static class WarehouseItemStorageView implements StorageView<ItemVariant> {
        private final PlayerWarehouse warehouse;
        private final ItemVariant variant;
        private final long amount;
        private final Consumer<String> onCommitted;

        public WarehouseItemStorageView(PlayerWarehouse warehouse, ItemVariant variant, long amount, Consumer<String> onCommitted) {
            this.warehouse = warehouse;
            this.variant = variant;
            this.amount = amount;
            this.onCommitted = onCommitted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            if (!resource.equals(variant) || maxAmount <= 0) return 0;
            long requested = Math.min(amount, maxAmount);
            Map<PlayerWarehouse, Map<WarehouseStackKey, Long>> snapshot = new LinkedHashMap<>();
            for (PlayerWarehouse pw : warehouse.getSharedGroupWarehouses()) {
                snapshot.put(pw, pw.unifiedStorageSnapshot());
            }

            ItemStack extracted = warehouse.withSuppressedDirtyNotifications(() ->
                    WarehouseManager.takeMatching(warehouse, resource.toStack(1), (int) requested, true));
            long extractedCount = extracted.getCount();
            if (extractedCount <= 0) {
                restoreSharedGroup(snapshot);
                return 0;
            }
            
            transaction.addCloseCallback((context, result) -> {
                if (result.wasCommitted()) {
                    onCommitted.accept("bound_barrel.view_extract");
                } else {
                    restoreSharedGroup(snapshot);
                }
            });
            return extractedCount;
        }

        @Override
        public boolean isResourceBlank() {
            return variant.isBlank();
        }

        @Override
        public ItemVariant getResource() {
            return variant;
        }

        @Override
        public long getAmount() {
            return amount;
        }

        @Override
        public long getCapacity() {
            return warehouse.getMaxItemStackSize();
        }
    }
}

