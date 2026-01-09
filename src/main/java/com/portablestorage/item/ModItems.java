package com.portablestorage.item;

import com.portablestorage.PortableStorage;
import com.portablestorage.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final Item STORAGE_KEY = register("storage_key", new StorageKeyItem(new Item.Properties().stacksTo(1)));
    
    // 绑定木桶物品
    public static final Item BOUND_BARREL = register("bound_barrel", new BoundBarrelItem(ModBlocks.BOUND_BARREL, new Item.Properties().stacksTo(64)));

    // 虚拟流体物品
    public static final Item VIRTUAL_LAVA = register("lava", new VirtualFluidItem(new Item.Properties()));
    public static final Item VIRTUAL_WATER = register("water", new VirtualFluidItem(new Item.Properties()));
    public static final Item VIRTUAL_MILK = register("milk", new VirtualFluidItem(new Item.Properties()));
    public static final Item BOTTLED_EXPERIENCE = register("bottled_experience", new Item(new Item.Properties().stacksTo(64)));

    // 彩蛋物品
    public static final Item EASTER_EGG = register("easter_egg", new EasterEggItem(new Item.Properties().stacksTo(64)));

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, PortableStorage.id(name), item);
    }

    public static void registerModItems() {
        PortableStorage.LOGGER.info("Registering Mod Items for " + PortableStorage.MOD_ID);
    }
}

