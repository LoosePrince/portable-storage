package com.portablestorage.entity;

import com.portablestorage.PortableStorage;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final ResourceKey<EntityType<?>> RIFT_AVATAR_KEY = ResourceKey.create(
            BuiltInRegistries.ENTITY_TYPE.key(),
            PortableStorage.id("rift_avatar"));

    public static final EntityType<RiftAvatarEntity> RIFT_AVATAR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PortableStorage.id("rift_avatar"),
            EntityType.Builder.of(RiftAvatarEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .noSummon() // 优化：禁止玩家通过指令召唤
                    .noSave() // 优化：不随区块保存，由我们的 Manager 动态管理
                    .build(RIFT_AVATAR_KEY));

    public static void registerModEntities() {
        PortableStorage.LOGGER.info("Registering Mod Entities for " + PortableStorage.MOD_ID);
    }
}
