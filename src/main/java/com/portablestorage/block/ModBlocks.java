package com.portablestorage.block;

import com.portablestorage.PortableStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
    public static final Block BOUND_BARREL = register("bound_barrel",
            new BoundBarrelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));

    public static final Block TEMP_BED = register("temp_bed",
            new TempBedBlock(net.minecraft.world.item.DyeColor.RED,
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.RED_BED)));

    private static <T extends Block> T register(String name, T block) {
        return Registry.register(BuiltInRegistries.BLOCK, PortableStorage.id(name), block);
    }

    public static void registerModBlocks() {
        PortableStorage.LOGGER.info("Registering Mod Blocks for " + PortableStorage.MOD_ID);
    }
}
