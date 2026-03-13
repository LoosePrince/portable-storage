package com.portablestorage.block;

import java.util.function.Function;

import com.portablestorage.PortableStorage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
    public static final Block BOUND_BARREL = register("bound_barrel",
            BoundBarrelBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava());

    public static final Block TEMP_BED = register("temp_bed",
            properties -> new TempBedBlock(net.minecraft.world.item.DyeColor.RED, properties),
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.RED_BED));

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PortableStorage.id(name));
        T block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, PortableStorage.id(name), block);
    }

    public static void registerModBlocks() {
        PortableStorage.LOGGER.info("Registering Mod Blocks for " + PortableStorage.MOD_ID);
    }
}
