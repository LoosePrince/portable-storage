package com.portablestorage.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MyWarehouseComponent implements WarehouseComponent {
    private final Map<UUID, PlayerWarehouse> warehouses = new HashMap<>();
    private final Level level;

    public MyWarehouseComponent(Level level) {
        this.level = level;
    }

    @Override
    public PlayerWarehouse getWarehouse(UUID uuid) {
        // 确保同一个 UUID 始终返回同一个 PlayerWarehouse 实例
        return warehouses.computeIfAbsent(uuid, k -> new PlayerWarehouse(k, (warehouse) -> {
            if (!level.isClientSide) {
                syncForPlayer(k);
            }
        }));
    }

    @Override
    public void syncForPlayer(UUID uuid) {
        if (level.isClientSide) return;
        // 触发世界组件同步
        ModComponents.WAREHOUSE.sync(level);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        // 关键修复：不要 clear()，否则客户端已打开的界面引用的实例会失效
        CompoundTag warehousesTag = tag.getCompound("warehouses");
        for (String key : warehousesTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                // 获取（或创建）实例并更新其内容
                PlayerWarehouse warehouse = getWarehouse(uuid);
                warehouse.readNbt(warehousesTag.getCompound(key), registries);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag warehousesTag = new CompoundTag();
        for (Map.Entry<UUID, PlayerWarehouse> entry : warehouses.entrySet()) {
            CompoundTag warehouseTag = new CompoundTag();
            entry.getValue().writeNbt(warehouseTag, registries);
            warehousesTag.put(entry.getKey().toString(), warehouseTag);
        }
        tag.put("warehouses", warehousesTag);
    }
}
