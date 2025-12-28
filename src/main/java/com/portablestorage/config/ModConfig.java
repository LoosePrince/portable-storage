package com.portablestorage.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class ModConfig {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("portablestorage.toml");
    
    public static boolean offsetInventory = true;
    public static boolean hideRecipeBook = true;
    public static boolean enable3x3Crafting = true;
    public static boolean dropStorageOnDeath = true;
    
    // 仓库限制配置
    public static int maxStorageTypes = -1;
    public static long maxItemStackSize = -1;
    public static int baseMaxStorageTypes = 54;
    public static long baseMaxItemStackSize = -1;
    
    // 运行时启用的 3x3 合成状态，由服务端下发决定
    private static boolean active3x3Crafting = true;

    public static boolean is3x3Enabled() {
        return active3x3Crafting;
    }

    public static void setActive3x3Crafting(boolean value) {
        active3x3Crafting = value;
    }

    public static void load() {
        CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_FILE, TomlFormat.instance())
                .defaultResource("/assets/portablestorage/config/default-config.toml")
                .writingMode(WritingMode.REPLACE)
                .build();
        
        config.load();

        offsetInventory = config.getOrElse("client.offsetInventory", true);
        hideRecipeBook = config.getOrElse("client.hideRecipeBook", true);
        enable3x3Crafting = config.getOrElse("server.enable3x3Crafting", true);
        dropStorageOnDeath = config.getOrElse("server.dropStorageOnDeath", true);
        
        maxStorageTypes = ((Number) config.getOrElse("server.maxStorageTypes", -1)).intValue();
        maxItemStackSize = ((Number) config.getOrElse("server.maxItemStackSize", -1L)).longValue();
        baseMaxStorageTypes = ((Number) config.getOrElse("server.baseMaxStorageTypes", 54)).intValue();
        baseMaxItemStackSize = ((Number) config.getOrElse("server.baseMaxItemStackSize", -1L)).longValue();
        
        // 初始时设置为本地配置值
        active3x3Crafting = enable3x3Crafting;

        if (offsetInventory) {
            hideRecipeBook = true;
        }
        
        config.close();
    }

    public static void save() {
        CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_FILE, TomlFormat.instance())
                .writingMode(WritingMode.REPLACE)
                .build();
        config.load();
        config.set("client.offsetInventory", offsetInventory);
        config.set("client.hideRecipeBook", hideRecipeBook);
        config.set("server.enable3x3Crafting", enable3x3Crafting);
        config.set("server.dropStorageOnDeath", dropStorageOnDeath);
        
        config.set("server.maxStorageTypes", maxStorageTypes);
        config.set("server.maxItemStackSize", maxItemStackSize);
        config.set("server.baseMaxStorageTypes", baseMaxStorageTypes);
        config.set("server.baseMaxItemStackSize", baseMaxItemStackSize);
        
        config.save();
        config.close();
    }
}

