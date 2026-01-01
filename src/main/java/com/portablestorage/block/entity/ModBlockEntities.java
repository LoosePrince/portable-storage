package com.portablestorage.block.entity;

import com.portablestorage.PortableStorage;
import com.portablestorage.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
    public static final BlockEntityType<BoundBarrelBlockEntity> BOUND_BARREL = register("bound_barrel",
        BlockEntityType.Builder.of(BoundBarrelBlockEntity::new, ModBlocks.BOUND_BARREL).build());

    private static <T extends BlockEntityType<?>> T register(String name, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PortableStorage.id(name), type);
    }

    public static void registerModBlockEntities() {
        PortableStorage.LOGGER.info("Registering Mod Block Entities for " + PortableStorage.MOD_ID);
    }
}

