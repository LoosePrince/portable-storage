package com.portablestorage.item;

import java.util.function.Function;

import com.portablestorage.PortableStorage;
import com.portablestorage.block.ModBlocks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final Item STORAGE_KEY = register("storage_key",
            StorageKeyItem::new,
            new Item.Properties().stacksTo(1));
    
    // 绑定木桶物品
    public static final Item BOUND_BARREL = register("bound_barrel",
            properties -> new BoundBarrelItem(ModBlocks.BOUND_BARREL, properties),
            new Item.Properties().stacksTo(64));

    // 虚拟流体物品
    public static final Item VIRTUAL_LAVA = register("lava", VirtualFluidItem::new, new Item.Properties());
    public static final Item VIRTUAL_WATER = register("water", VirtualFluidItem::new, new Item.Properties());
    public static final Item VIRTUAL_MILK = register("milk", VirtualFluidItem::new, new Item.Properties());
    public static final Item BOTTLED_EXPERIENCE = register("bottled_experience", Item::new,
            new Item.Properties().stacksTo(64));

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory,
            Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PortableStorage.id(name));
        T item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, PortableStorage.id(name), item);
    }

    public static void registerModItems() {
        PortableStorage.LOGGER.info("Registering Mod Items for " + PortableStorage.MOD_ID);
    }
}

