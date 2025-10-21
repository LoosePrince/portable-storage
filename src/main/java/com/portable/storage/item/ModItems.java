package com.portable.storage.item;

import com.portable.storage.PortableStorage;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 模组物品注册
 */
public class ModItems {
    
    public static final Item STORAGE_KEY = new StorageKeyItem(new Item.Settings());
    
    public static void register() {
        Registry.register(Registries.ITEM, Identifier.of(PortableStorage.MOD_ID, "storage_key"), STORAGE_KEY);
    }
}