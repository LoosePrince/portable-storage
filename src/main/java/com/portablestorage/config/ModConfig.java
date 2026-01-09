package com.portablestorage.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.portablestorage.util.StoragePosition;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class ModConfig {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("portablestorage.toml");
    
    public static boolean offsetInventory = true;
    public static boolean hideRecipeBook = true;
    public static boolean showSmallIcons = true;
    public static boolean removeExperimentalWarning = true;
    public static boolean allowHotReload = false;
    public static boolean enable3x3Crafting = true;
    public static boolean dropStorageOnDeath = true;
    public static String unconditionalWarehouse = "NONE";
    public static StoragePosition storagePosition = StoragePosition.BOTTOM;
    
    // 仓库限制配置
    public static int maxStorageTypes = -1;
    public static long maxItemStackSize = -1;
    public static int baseMaxStorageTypes = 54;
    public static long baseMaxItemStackSize = -1;
    public static int maxItemNbtSize = 10240; // 默认10KB，-1为不限制
    
    // 漏斗升级配置
    public static int hopperRange = 5;
    public static double hopperFrequency = 1.0;

    // 无限流体配置
    public static long lavaInfiniteThreshold = 10000;
    public static long waterInfiniteThreshold = 2;
    
    // 裂隙升级配置
    public static String riftUpgradeItem = "minecraft:dragon_egg";
    public static int riftChunkSize = 1;
    public static boolean enableRiftForcedLoading = true;
    public static int riftForcedLoadingRange = 1;
    
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
        showSmallIcons = config.getOrElse("client.showSmallIcons", false);
        removeExperimentalWarning = config.getOrElse("client.removeExperimentalWarning", true);
        try {
            storagePosition = StoragePosition.valueOf(config.getOrElse("client.storagePosition", "BOTTOM").toUpperCase());
        } catch (IllegalArgumentException e) {
            storagePosition = StoragePosition.BOTTOM;
        }
        allowHotReload = config.getOrElse("server.allowHotReload", false);
        enable3x3Crafting = config.getOrElse("server.enable3x3Crafting", true);
        dropStorageOnDeath = config.getOrElse("server.dropStorageOnDeath", true);
        unconditionalWarehouse = config.getOrElse("server.unconditionalWarehouse", "NONE").toUpperCase();
        
        maxStorageTypes = ((Number) config.getOrElse("server.maxStorageTypes", -1)).intValue();
        maxItemStackSize = ((Number) config.getOrElse("server.maxItemStackSize", -1L)).longValue();
        baseMaxStorageTypes = ((Number) config.getOrElse("server.baseMaxStorageTypes", 54)).intValue();
        baseMaxItemStackSize = ((Number) config.getOrElse("server.baseMaxItemStackSize", -1L)).longValue();
        maxItemNbtSize = ((Number) config.getOrElse("server.maxItemNbtSize", 10240)).intValue();
        hopperRange = ((Number) config.getOrElse("server.hopperRange", 5)).intValue();
        hopperFrequency = ((Number) config.getOrElse("server.hopperFrequency", 1.0)).doubleValue();
        lavaInfiniteThreshold = ((Number) config.getOrElse("server.lavaInfiniteThreshold", 10000L)).longValue();
        waterInfiniteThreshold = ((Number) config.getOrElse("server.waterInfiniteThreshold", 2L)).longValue();
        riftUpgradeItem = config.getOrElse("server.riftUpgradeItem", "minecraft:dragon_egg");
        riftChunkSize = ((Number) config.getOrElse("server.riftChunkSize", 1)).intValue();
        enableRiftForcedLoading = config.getOrElse("server.enableRiftForcedLoading", true);
        riftForcedLoadingRange = ((Number) config.getOrElse("server.riftForcedLoadingRange", 1)).intValue();
        
        // 初始时设置为本地配置值
        active3x3Crafting = enable3x3Crafting;

        if (storagePosition.isHorizontal()) {
            hideRecipeBook = true;
        } else if (offsetInventory) {
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
        config.set("client.showSmallIcons", showSmallIcons);
        config.set("client.removeExperimentalWarning", removeExperimentalWarning);
        config.set("client.storagePosition", storagePosition.name());
        config.set("server.allowHotReload", allowHotReload);
        config.set("server.enable3x3Crafting", enable3x3Crafting);
        config.set("server.dropStorageOnDeath", dropStorageOnDeath);
        config.set("server.unconditionalWarehouse", unconditionalWarehouse);
        
        config.set("server.maxStorageTypes", maxStorageTypes);
        config.set("server.maxItemStackSize", maxItemStackSize);
        config.set("server.baseMaxStorageTypes", baseMaxStorageTypes);
        config.set("server.baseMaxItemStackSize", baseMaxItemStackSize);
        config.set("server.maxItemNbtSize", maxItemNbtSize);
        config.set("server.hopperRange", hopperRange);
        config.set("server.hopperFrequency", hopperFrequency);
        config.set("server.lavaInfiniteThreshold", lavaInfiniteThreshold);
        config.set("server.waterInfiniteThreshold", waterInfiniteThreshold);
        config.set("server.riftUpgradeItem", riftUpgradeItem);
        config.set("server.riftChunkSize", riftChunkSize);
        config.set("server.enableRiftForcedLoading", enableRiftForcedLoading);
        config.set("server.riftForcedLoadingRange", riftForcedLoadingRange);
        
        config.save();
        config.close();
    }
}

