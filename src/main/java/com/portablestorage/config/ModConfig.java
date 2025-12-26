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

    public static void load() {
        CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_FILE, TomlFormat.instance())
                .defaultResource("/assets/portablestorage/config/default-config.toml")
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();
        
        config.load();

        offsetInventory = config.getOrElse("offsetInventory", true);
        hideRecipeBook = config.getOrElse("hideRecipeBook", true);
        enable3x3Crafting = config.getOrElse("enable3x3Crafting", true);

        // 如果启用背包偏移，则强制隐藏配方书
        if (offsetInventory) {
            hideRecipeBook = true;
        }
        
        config.close();
    }
}

