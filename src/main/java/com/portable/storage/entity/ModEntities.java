package com.portable.storage.entity;

import com.portable.storage.PortableStorage;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<RiftAvatarEntity> RIFT_AVATAR = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(PortableStorage.MOD_ID, "rift_avatar"),
            EntityType.Builder.<RiftAvatarEntity>create(RiftAvatarEntity::new, SpawnGroup.MISC)
                    .disableSaving()
                    .disableSummon()
                    .dimensions(0.6F, 1.8F)
                    .build()
    );

    public static void register() {
        PortableStorage.LOGGER.info("Registering entities for " + PortableStorage.MOD_ID);
        try {
            // 为复制体注册默认属性
            FabricDefaultAttributeRegistry.register(RIFT_AVATAR, com.portable.storage.entity.RiftAvatarEntity.createAttributes());
        } catch (Throwable ignored) {}
    }
}


