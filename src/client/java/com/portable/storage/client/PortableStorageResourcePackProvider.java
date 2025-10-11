package com.portable.storage.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class PortableStorageResourcePackProvider {
    
    public static void register() {
        ModContainer mod = FabricLoader.getInstance().getModContainer("portable-storage").orElseThrow();
        
        // 注册内置资源包
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
            Identifier.of("portable-storage", "recipe_book_textures"),
            mod,
            "Portable Storage Recipe Book Textures",
            ResourcePackActivationType.NORMAL
        );
    }
}
