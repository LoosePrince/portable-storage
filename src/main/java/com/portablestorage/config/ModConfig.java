package com.portablestorage.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.portablestorage.util.StoragePosition;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * 模组配置类
 * 管理模组的配置项，包括客户端和服务端配置
 */
public class ModConfig {
    /** 配置文件路径 */
    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("portablestorage.toml");
    }
    
    // ========== 客户端配置 ==========
    /** 是否偏移背包界面 */
    public static boolean offsetInventory = true;
    /** 是否隐藏配方书 */
    public static boolean hideRecipeBook = true;
    /** 是否显示小图标 */
    public static boolean showSmallIcons = true;
    /** 是否移除实验性功能警告 */
    public static boolean removeExperimentalWarning = true;
    /** 是否在关闭容器界面时自动折叠仓库 */
    public static boolean autoFoldOnClose = false;
    /** 仓库显示位置 */
    public static StoragePosition storagePosition = StoragePosition.BOTTOM;
    
    // ========== 服务端配置 ==========
    /** 是否允许热重载配置 */
    public static boolean allowHotReload = false;
    /** 是否启用 3x3 合成 */
    public static boolean enable3x3Crafting = true;
    /** 死亡时是否掉落存储钥匙 */
    public static boolean dropStorageOnDeath = true;
    /** 无条件开启仓库的模式（NONE/BASE/FULL） */
    public static String unconditionalWarehouse = "NONE";
    /** 基础仓库激活道具的物品 ID（默认海洋之心） */
    public static String baseWarehouseActivationItem = "minecraft:heart_of_the_sea";
    /** 完整仓库激活道具的物品 ID（默认下界之星） */
    public static String fullWarehouseActivationItem = "minecraft:nether_star";
    
    // ========== 仓库限制配置 ==========
    /** 最大存储类型数（-1 为不限制） */
    public static int maxStorageTypes = -1;
    /** 最大物品堆叠数（-1 为不限制） */
    public static long maxItemStackSize = -1;
    /** 基础最大存储类型数 */
    public static int baseMaxStorageTypes = 54;
    /** 基础最大物品堆叠数（-1 为不限制） */
    public static long baseMaxItemStackSize = -1;
    /** 最大物品 NBT 大小（字节，默认 10KB，-1 为不限制） */
    public static int maxItemNbtSize = 10240;
    
    // ========== 漏斗升级配置 ==========
    /** 漏斗拾取范围 */
    public static int hopperRange = 5;
    /** 漏斗拾取频率（倍数） */
    public static double hopperFrequency = 1.0;

    // ========== 无限流体配置 ==========
    /** 熔岩无限阈值 */
    public static long lavaInfiniteThreshold = 10000;
    /** 水无限阈值 */
    public static long waterInfiniteThreshold = 2;
    
    // ========== 裂隙升级配置 ==========
    /** 裂隙升级物品 ID */
    public static String riftUpgradeItem = "minecraft:dragon_egg";
    /** 裂隙区块大小 */
    public static int riftChunkSize = 1;
    /** 裂隙地块间距（区块） */
    public static int riftPlotSpacingChunks = 64;
    /** 裂隙地板高度 */
    public static int riftFloorY = 64;
    /** 是否启用裂隙强制加载 */
    public static boolean enableRiftForcedLoading = true;
    /** 裂隙强制加载范围 */
    public static int riftForcedLoadingRange = 1;
    /** 是否启用裂隙复制体 */
    public static boolean enableRiftAvatar = true;
    /** 是否启用裂隙个人边界 */
    public static boolean enableRiftBorder = true;
    /** 裂隙边界警告距离 */
    public static int riftBorderWarningBlocks = 0;

    /** 是否启用潮涌核心升级 */
    public static boolean enableConduitUpgrade = true;
    
    // ========== 运行时状态 ==========
    /** 运行时启用的 3x3 合成状态（由服务端下发决定） */
    private static boolean active3x3Crafting = true;

    /**
     * 检查 3x3 合成是否启用
     * @return 是否启用
     */
    public static boolean is3x3Enabled() {
        return active3x3Crafting;
    }

    /**
     * 设置 3x3 合成状态
     * @param value 是否启用
     */
    public static void setActive3x3Crafting(boolean value) {
        active3x3Crafting = value;
    }

    /**
     * 加载配置文件
     */
    public static void load() {
        CommentedFileConfig config = CommentedFileConfig.builder(configFile(), TomlFormat.instance())
                .defaultResource("/assets/portablestorage/config/default-config.toml")
                .writingMode(WritingMode.REPLACE)
                .build();
        
        config.load();

        offsetInventory = config.getOrElse("client.offsetInventory", true);
        hideRecipeBook = config.getOrElse("client.hideRecipeBook", true);
        showSmallIcons = config.getOrElse("client.showSmallIcons", false);
        removeExperimentalWarning = config.getOrElse("client.removeExperimentalWarning", true);
        autoFoldOnClose = config.getOrElse("client.autoFoldOnClose", false);
        try {
            storagePosition = StoragePosition.valueOf(config.getOrElse("client.storagePosition", "BOTTOM").toUpperCase());
        } catch (IllegalArgumentException e) {
            storagePosition = StoragePosition.BOTTOM;
        }
        allowHotReload = config.getOrElse("server.allowHotReload", false);
        enable3x3Crafting = config.getOrElse("server.enable3x3Crafting", true);
        dropStorageOnDeath = config.getOrElse("server.dropStorageOnDeath", true);
        unconditionalWarehouse = config.getOrElse("server.unconditionalWarehouse", "NONE").toUpperCase();
        baseWarehouseActivationItem = config.getOrElse("server.baseWarehouseActivationItem", "minecraft:heart_of_the_sea");
        fullWarehouseActivationItem = config.getOrElse("server.fullWarehouseActivationItem", "minecraft:nether_star");
        
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
        riftPlotSpacingChunks = ((Number) config.getOrElse("server.riftPlotSpacingChunks", 64)).intValue();
        riftFloorY = ((Number) config.getOrElse("server.riftFloorY", 64)).intValue();
        enableRiftForcedLoading = config.getOrElse("server.enableRiftForcedLoading", true);
        riftForcedLoadingRange = ((Number) config.getOrElse("server.riftForcedLoadingRange", 1)).intValue();
        enableRiftAvatar = config.getOrElse("server.enableRiftAvatar", true);
        enableRiftBorder = config.getOrElse("server.enableRiftBorder", true);
        riftBorderWarningBlocks = ((Number) config.getOrElse("server.riftBorderWarningBlocks", 0)).intValue();
        enableConduitUpgrade = config.getOrElse("server.enableConduitUpgrade", true);
        
        // 初始时设置为本地配置值
        active3x3Crafting = enable3x3Crafting;

        if (storagePosition.isHorizontal()) {
            hideRecipeBook = true;
        } else if (offsetInventory) {
            hideRecipeBook = true;
        }
        
        config.close();
    }

    /**
     * 保存配置到文件
     */
    public static void save() {
        CommentedFileConfig config = CommentedFileConfig.builder(configFile(), TomlFormat.instance())
                .writingMode(WritingMode.REPLACE)
                .build();
        config.load();
        config.set("client.offsetInventory", offsetInventory);
        config.set("client.hideRecipeBook", hideRecipeBook);
        config.set("client.showSmallIcons", showSmallIcons);
        config.set("client.removeExperimentalWarning", removeExperimentalWarning);
        config.set("client.autoFoldOnClose", autoFoldOnClose);
        config.set("client.storagePosition", storagePosition.name());
        config.set("server.allowHotReload", allowHotReload);
        config.set("server.enable3x3Crafting", enable3x3Crafting);
        config.set("server.dropStorageOnDeath", dropStorageOnDeath);
        config.set("server.unconditionalWarehouse", unconditionalWarehouse);
        config.set("server.baseWarehouseActivationItem", baseWarehouseActivationItem);
        config.set("server.fullWarehouseActivationItem", fullWarehouseActivationItem);
        
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
        config.set("server.riftPlotSpacingChunks", riftPlotSpacingChunks);
        config.set("server.riftFloorY", riftFloorY);
        config.set("server.enableRiftForcedLoading", enableRiftForcedLoading);
        config.set("server.riftForcedLoadingRange", riftForcedLoadingRange);
        config.set("server.enableRiftAvatar", enableRiftAvatar);
        config.set("server.enableRiftBorder", enableRiftBorder);
        config.set("server.riftBorderWarningBlocks", riftBorderWarningBlocks);
        config.set("server.enableConduitUpgrade", enableConduitUpgrade);
        
        config.save();
        config.close();
    }
}

