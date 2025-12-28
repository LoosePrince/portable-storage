package com.portablestorage.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Scoreboard;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MyWarehouseComponent implements WarehouseComponent {
    private final Map<UUID, PlayerWarehouse> warehouses = new HashMap<>();
    private final Scoreboard scoreboard;
    private final MinecraftServer server;

    public MyWarehouseComponent(Scoreboard scoreboard, MinecraftServer server) {
        this.scoreboard = scoreboard;
        this.server = server;
    }

    @Override
    public PlayerWarehouse getWarehouse(UUID uuid) {
        // 确保同一个 UUID 始终返回同一个 PlayerWarehouse 实例
        return warehouses.computeIfAbsent(uuid, k -> new PlayerWarehouse(k, (warehouse) -> {
            sync();
        }));
    }

    @Override
    public void syncForPlayer(UUID uuid) {
        sync();
    }

    private void sync() {
        // Scoreboard 组件同步是全局的
        ModComponents.WAREHOUSE.sync(scoreboard);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag warehousesTag = tag.getCompound("warehouses");
        for (String key : warehousesTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
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
