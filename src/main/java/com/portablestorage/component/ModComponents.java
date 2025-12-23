package com.portablestorage.component;

import net.minecraft.resources.ResourceLocation;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

public class ModComponents {
    public static final ComponentKey<WarehouseComponent> WAREHOUSE = 
        ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("portablestorage", "warehouse"), WarehouseComponent.class);
}
