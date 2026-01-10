package com.portablestorage.component;

import com.portablestorage.PortableStorage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;

public class ModComponents {
    public static final ComponentKey<WarehouseComponent> WAREHOUSE = 
        ComponentRegistry.getOrCreate(PortableStorage.id("warehouse"), WarehouseComponent.class);

    /**
     * 从 Level 获取仓库组件（通过计分板实现跨维度）
     */
    public static WarehouseComponent get(Level level) {
        return WAREHOUSE.get(level.getScoreboard());
    }

    /**
     * 从实体获取仓库组件（通过实体所在世界的计分板）
     */
    public static WarehouseComponent get(Entity entity) {
        return WAREHOUSE.get(entity.level().getScoreboard());
    }

    /**
     * 从服务器获取指定 UUID 的玩家仓库
     */
    public static PlayerWarehouse getWarehouse(net.minecraft.server.MinecraftServer server, java.util.UUID uuid) {
        if (server == null) return null;
        return WAREHOUSE.get(server.getScoreboard()).getWarehouse(uuid);
    }
}
