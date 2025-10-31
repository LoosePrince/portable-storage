package com.portable.storage.blockentity;

import com.portable.storage.PortableStorage;
import com.portable.storage.block.ModBlocks;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 注册自定义方块实体
 */
public class ModBlockEntities {
    public static final BlockEntityType<BoundBarrelBlockEntity> BOUND_BARREL = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        Identifier.of(PortableStorage.MOD_ID, "bound_barrel"),
        BlockEntityType.Builder.create(
            BoundBarrelBlockEntity::new,
            ModBlocks.BOUND_BARREL
        ).build(null)
    );

    public static void register() {
        PortableStorage.LOGGER.info("Registering block entities for " + PortableStorage.MOD_ID);
    }
}

