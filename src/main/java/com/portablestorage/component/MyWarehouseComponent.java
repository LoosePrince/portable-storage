package com.portablestorage.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MyWarehouseComponent implements WarehouseComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger("PortableStorage/MyWarehouseComponent");
    private final Map<UUID, PlayerWarehouse> warehouses = new HashMap<>();
    private final Scoreboard scoreboard;
    private final MinecraftServer server;

    public MyWarehouseComponent(Scoreboard scoreboard, MinecraftServer minecraftServer) {
        this.scoreboard = scoreboard;
        this.server = minecraftServer;
    }

    @Override
    public PlayerWarehouse getWarehouse(UUID uuid) {
        // 确保同一个 UUID 始终返回同一个 PlayerWarehouse 实例
        PlayerWarehouse pw = warehouses.computeIfAbsent(uuid, k -> {
            PlayerWarehouse newPw = new PlayerWarehouse(k, (warehouse) -> {
                sync();
            });
            newPw.setParentComponent(this);
            return newPw;
        });

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
        // Scoreboard 组件同步是全局的
        ModComponents.WAREHOUSE.sync(scoreboard);
    }

    @Override
    public void readFromNbt(CompoundTag tag) {
        CompoundTag warehousesTag = tag.getCompound("warehouses");
        for (String key : warehousesTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerWarehouse warehouse = getWarehouse(uuid);
                CompoundTag warehouseTag = warehousesTag.getCompound(key);
                // 在 CCA 5.2.3 中，Component 的 readFromNbt 不需要 registries 参数
                // 但 PlayerWarehouse.readNbt 需要，所以我们需要从服务器获取
                if (server != null) {
                    warehouse.readNbt(warehouseTag, server.registryAccess());
                } else {
                    // 客户端也需要读取，但需要从客户端世界获取 registryAccess
                    // 使用 BuiltInRegistries 创建 registryAccess（这是客户端和服务器端都可用的一致方案）
                    HolderLookup.Provider registryAccess = null;
                    try {
                        registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                    } catch (Exception e) {
                        LOGGER.warn("无法创建 registryAccess，仓库数据可能无法正确加载", e);
                    }
                    
                    // 如果成功获取了 registryAccess，读取仓库数据
                    if (registryAccess != null) {
                        warehouse.readNbt(warehouseTag, registryAccess);
                    } else {
                        LOGGER.warn("无法获取 registryAccess，跳过仓库数据读取。这可能导致仓库按钮不显示。");
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        CompoundTag warehousesTag = new CompoundTag();
        for (Map.Entry<UUID, PlayerWarehouse> entry : warehouses.entrySet()) {
            CompoundTag warehouseTag = new CompoundTag();
            // 在 CCA 5.2.3 中，Component 的 writeToNbt 不需要 registries 参数
            // 但 PlayerWarehouse.writeNbt 需要，所以我们需要从服务器获取
            if (server != null) {
                entry.getValue().writeNbt(warehouseTag, server.registryAccess());
            }
            warehousesTag.put(entry.getKey().toString(), warehouseTag);
        }
        tag.put("warehouses", warehousesTag);
    }
}
