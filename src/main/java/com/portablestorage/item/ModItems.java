package com.portablestorage.item;

import com.portablestorage.PortableStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final Item STORAGE_KEY = register("storage_key", new StorageKeyItem(new Item.Properties().stacksTo(1)));

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(PortableStorage.MOD_ID, name), item);
    }

    public static void registerModItems() {
        PortableStorage.LOGGER.info("Registering Mod Items for " + PortableStorage.MOD_ID);
    }
}

