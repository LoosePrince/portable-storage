package com.portable.storage.block;

import com.portable.storage.PortableStorage;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 注册自定义方块
 */
public class ModBlocks {
    public static final Block BOUND_BARREL = registerBlock("bound_barrel",
        new BoundBarrelBlock(AbstractBlock.Settings.copy(Blocks.BARREL)));
    
    public static final Block TEMP_BED = registerBlock("temp_bed",
        new TempBedBlock(net.minecraft.util.DyeColor.RED, AbstractBlock.Settings.copy(Blocks.RED_BED)));

    private static Block registerBlock(String name, Block block) {
        // 不注册 BlockItem，因为绑定木桶是通过放置普通木桶创建的
        return Registry.register(Registries.BLOCK, Identifier.of(PortableStorage.MOD_ID, name), block);
    }

    public static void register() {
        PortableStorage.LOGGER.info("Registering blocks for " + PortableStorage.MOD_ID);
    }
}

