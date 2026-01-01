package com.portablestorage.screen;

import com.portablestorage.PortableStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

public class ModScreenHandlers {
    public static final MenuType<CraftingWarehouseScreenHandler> CRAFTING_WAREHOUSE = 
        new MenuType<>(CraftingWarehouseScreenHandler::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);

    public static final MenuType<BoundBarrelScreenHandler> BOUND_BARREL = 
        new MenuType<>(BoundBarrelScreenHandler::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);

    public static void register() {
        Registry.register(BuiltInRegistries.MENU, PortableStorage.id("crafting_warehouse"), CRAFTING_WAREHOUSE);
        Registry.register(BuiltInRegistries.MENU, PortableStorage.id("bound_barrel"), BOUND_BARREL);
    }
}

