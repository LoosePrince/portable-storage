package com.portablestorage.component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.Scoreboard;

public class MyWarehouseComponent implements WarehouseComponent {
    private final Map<UUID, PlayerWarehouse> warehouses = new HashMap<>();
    private final Scoreboard scoreboard;
    private final MinecraftServer server;

    public MyWarehouseComponent(Scoreboard scoreboard, MinecraftServer minecraftServer) {
        this.scoreboard = scoreboard;
        this.server = minecraftServer;
    }

    private PlayerWarehouse createWarehouse(UUID uuid) {
        PlayerWarehouse warehouse = new PlayerWarehouse(uuid, ignored -> sync());
        warehouse.setParentComponent(this);
        return warehouse;
    }

    @Override
    public PlayerWarehouse getWarehouse(UUID uuid) {
        // 确保同一个 UUID 始终返回同一个 PlayerWarehouse 实例
        PlayerWarehouse pw = warehouses.computeIfAbsent(uuid, this::createWarehouse);

        // 如果服务器在线且能找到对应玩家，更新名字以供客户端显示
        if (server != null) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                pw.setOwnerName(player.getScoreboardName());
            }
        }

        return pw;
    }

    @Override
    public void syncForPlayer(UUID uuid) {
        sync();
    }

    @Override
    public java.util.Collection<PlayerWarehouse> getAllWarehouses() {
        return warehouses.values();
    }

    private void sync() {
        ModComponents.WAREHOUSE.sync(scoreboard);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void readData(ValueInput input) {
        HolderLookup.Provider registries = server != null ? server.registryAccess() : input.lookup();
        if (registries == null) {
            return;
        }

        Map<UUID, PlayerWarehouse> existing = new HashMap<>(warehouses);
        warehouses.clear();

        input.list("warehouses", CompoundTag.CODEC).ifPresent(list -> {
            for (CompoundTag warehouseTag : list) {
                String idString = warehouseTag.getString("id").orElse("");
                if (idString.isEmpty())
                    continue;

                try {
                    UUID uuid = UUID.fromString(idString);
                    PlayerWarehouse warehouse = existing.remove(uuid);
                    if (warehouse == null) {
                        warehouse = createWarehouse(uuid);
                    } else {
                        warehouse.setParentComponent(this);
                    }

                    warehouses.put(uuid, warehouse);
                    CompoundTag dataTag = warehouseTag.getCompoundOrEmpty("data");
                    warehouse.readNbt(dataTag, registries);
                } catch (IllegalArgumentException ignored) {
                }
            }
        });

        // 读取完成后不再输出调试日志，避免刷屏
    }

    @Override
    public void writeData(ValueOutput output) {
        if (server == null) {
            return;
        }

        HolderLookup.Provider registries = server.registryAccess();
        ValueOutput.TypedOutputList<CompoundTag> list = output.list("warehouses", CompoundTag.CODEC);

        for (Map.Entry<UUID, PlayerWarehouse> entry : warehouses.entrySet()) {
            CompoundTag warehouseTag = new CompoundTag();
            warehouseTag.putString("id", entry.getKey().toString());

            CompoundTag dataTag = new CompoundTag();
            entry.getValue().writeNbt(dataTag, registries);
            warehouseTag.put("data", dataTag);

            list.add(warehouseTag);
        }
    }
}
