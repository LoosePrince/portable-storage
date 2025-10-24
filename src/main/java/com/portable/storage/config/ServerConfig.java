package com.portable.storage.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.portable.storage.PortableStorage;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 服务端配置管理器
 * 负责加载、解析和保存服务端配置文件
 */
public class ServerConfig {
    private static final String CONFIG_FILE_NAME = "portable-storage-server.toml";
    private static ServerConfig INSTANCE = new ServerConfig();
    
    // 配置项
    private boolean requireConditionToEnable = true;
    private String enableItem = "minecraft:nether_star";
    private boolean consumeEnableItem = true;
    private boolean enableIncrementalSync = false;
    private boolean enableOnDemandSync = true;
    private int incrementalSyncIntervalTicks = 2;
    private int incrementalSyncMaxEntries = 512;
    private boolean clearStorageOnEnable = true;
    
    // 裂隙功能配置
    private boolean enableRiftFeature = true;
    private String riftUpgradeItem = "block:minecraft:dragon_egg";
    private int riftSize = 1;
    private boolean limitRiftHeight = false;
    
    // 工作台虚拟合成功能配置
    private boolean enableVirtualCrafting = true;
    
    // 无限流体功能配置
    private boolean enableInfiniteLava = true;
    private boolean enableInfiniteWater = true;
    private int infiniteLavaThreshold = 10000;
    private int infiniteWaterThreshold = 2;
    
    // 存储大小限制配置
    private boolean enableSizeLimit = true;
    private long maxStorageSizeBytes = 100 * 1024; // 100KB
    
    // 工作台升级容器界面显示配置
    private boolean stonecutter = false;
    private boolean cartographyTable = false;
    private boolean smithingTable = false;
    private boolean grindstone = false;
    private boolean loom = false;
    private boolean furnace = false;
    private boolean smoker = false;
    private boolean blastFurnace = false;
    private boolean anvil = false;
    private boolean enchantingTable = false;
    private boolean brewingStand = false;
    private boolean beacon = false;
    private boolean chest = false;
    private boolean barrel = false;
    private boolean enderChest = false;
    private boolean shulkerBox = false;
    private boolean dispenser = false;
    private boolean dropper = false;
    private boolean crafter = false;
    private boolean hopper = false;
    private boolean trappedChest = false;
    private boolean hopperMinecart = false;
    private boolean chestMinecart = false;
    private boolean chestBoat = false;
    private boolean bambooChestRaft = false;
    
    private CommentedFileConfig config;
    private Path configPath;
    
    private ServerConfig() {
        // 获取配置文件路径
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(CONFIG_FILE_NAME);
    }
    
    public static ServerConfig getInstance() {
        return INSTANCE;
    }
    
    /**
     * 加载配置文件
     */
    public void load() {
        try {
            // 如果配置文件不存在，从资源文件复制默认配置
            if (!Files.exists(configPath)) {
                copyDefaultConfig();
            }
            
            // 加载配置文件（带注释能力）
            config = CommentedFileConfig.builder(configPath).build();
            config.load();
            
            // 读取配置值
            boolean changed = ensureDefaultsWritten();
            readConfigValues();
            // 不调用 config.save()，改为“面向文本”的最小化写回，避免丢失其它注释
            if (changed) {
                writeMissingKeysWithCommentsTextually();
            }
            
            PortableStorage.LOGGER.info("成功加载服务端配置文件: {}", configPath);
        } catch (Exception e) {
            PortableStorage.LOGGER.error("加载服务端配置文件失败，使用默认配置", e);
            setDefaultValues();
        }
    }
    
    /**
     * 保存配置文件
     */
    public void save() {
        if (config != null) {
            try {
                writeConfigValues();
                config.save();
                PortableStorage.LOGGER.debug("保存服务端配置文件: {}", configPath);
            } catch (Exception e) {
                PortableStorage.LOGGER.error("保存服务端配置文件失败", e);
            }
        }
    }
    
    /**
     * 从资源文件复制默认配置
     */
    private void copyDefaultConfig() throws IOException {
        // 从模组资源中复制默认配置文件
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (inputStream != null) {
                Files.copy(inputStream, configPath, StandardCopyOption.REPLACE_EXISTING);
                PortableStorage.LOGGER.info("已创建默认服务端配置文件: {}", configPath);
            } else {
                PortableStorage.LOGGER.warn("未找到默认配置文件资源: {}", CONFIG_FILE_NAME);
                createDefaultConfigFile();
            }
        }
    }
    
    /**
     * 创建默认配置文件
     */
    private void createDefaultConfigFile() throws IOException {
        String defaultConfig = """
            # Portable Storage 服务端配置文件
            # 此配置文件控制服务端行为，修改后需要重启服务器生效
            
            [storage]
            # 是否需要条件启用玩家随身仓库
            # 启用后玩家的随身仓库不再是默认就有的，而是需要达成条件才会有
            # 默认值: true
            require_condition_to_enable = true
            
            # 启用玩家随身仓库的道具
            # 启用"需要条件启用玩家随身仓库"后用以启用玩家仓库的物品，手持右键以启用
            # 默认值: "minecraft:nether_star" (下界之星)
            # 格式: "命名空间:物品ID"
            enable_item = "minecraft:nether_star"
            
            # 启用玩家随身仓库的道具是否消耗
            # 开启后启用玩家随身仓库时会消耗该物品一个
            # 默认值: true
            consume_enable_item = true
            
            # 是否启用增量同步
            # 启用后只同步发生变化的数据，减少网络传输量
            # 可能存在BUG未发现或未修复的错误
            # 默认值: false
            enable_incremental_sync = false
            
            # 是否启用按需同步
            # 启用后只在玩家查看仓库界面时才同步，否则积攒变化
            # 可以显著减少服务器负载，但可能导致数据延迟
            # 默认值: true
            enable_on_demand_sync = true
            
            # 增量同步发送间隔（tick）
            # 数值越小，更新越及时，但网络与CPU负载更高
            # 默认值: 2
            incremental_sync_interval_ticks = 2
            
            # 单包最大增量条目数（upserts+removes 合计上限）
            # 超过将分多包发送
            # 默认值: 512
            incremental_sync_max_entries = 512
            
            # 启用仓库时是否清空现有数据
            # 启用后使用仓库激活物品时会清空玩家现有的仓库数据
            # 默认值: true
            clear_storage_on_enable = true
            
            # 是否启用裂隙功能
            # 启用后玩家可以通过裂隙升级槽进入私人裂隙空间
            # 禁用后无法进入裂隙，处于裂隙的玩家会在上线时被送回原位置
            # 默认值: true
            enable_rift_feature = true
            
            # 裂隙升级物品
            # 用于进入裂隙的升级槽物品，默认龙蛋
            # 格式: "类型:命名空间:物品ID" (类型为 block 或 item)
            # 默认值: "block:minecraft:dragon_egg"
            rift_upgrade_item = "block:minecraft:dragon_egg"
            
            # 裂隙大小
            # 裂隙空间的大小，单位为区块（16x16方块）
            # 默认值: 1
            rift_size = 1
            
            # 限制高度范围
            # 启用后限制裂隙内的高度范围，启用上下回传和屏障封底顶
            # 关闭后玩家可以在整个维度高度范围内建造
            # 
            # 限制模式（true）：
            # - 可建造高度：Y=0 到 Y=163（共164层）
            # - 底部Y=0-8层和顶部Y=155-163层会生成屏障方块作为边界保护
            # - 玩家无法在限制范围外放置或破坏方块
            # 
            # 非限制模式（false）：
            # - 可建造高度：Y=-64 到 Y=319（共384层，整个维度高度）
            # - 不生成屏障方块，无高度限制
            # - 玩家可以在整个维度高度范围内自由建造
            # 
            # 默认值: false
            limit_rift_height = false
            
            # 启用工作台虚拟合成功能
            # 启用后工作台升级槽位提供3x3虚拟合成覆盖层
            # 关闭后工作台升级槽位的虚拟合成描述也不会显示
            # 默认值: true
            enable_virtual_crafting = true
            
            # 启用无限岩浆功能
            # 启用后岩浆流体在仓库内数量大于阈值时数量显示∞（无限）
            # 存取都不会使其数量变化
            # 默认值: true
            enable_infinite_lava = true
            
            # 启用无限水功能
            # 启用后水流体在仓库内数量大于阈值时数量显示∞（无限）
            # 存取都不会使其数量变化
            # 默认值: true
            enable_infinite_water = true
            
            # 无限岩浆阈值
            # 岩浆流体数量大于此值时显示为无限
            # 默认值: 10000
            infinite_lava_threshold = 10000
            
            # 无限水阈值
            # 水流体数量大于此值时显示为无限
            # 默认值: 2
            infinite_water_threshold = 2
            
            # 是否启用物品大小限制
            # 启用后限制单个物品的大小，防止过大的物品存入存储
            # 默认值: true
            enable_size_limit = true
            
            # 最大物品大小（字节）
            # 单个物品的大小不能超过此值
            # 默认值: 102400 (100KB)
            max_storage_size_bytes = 102400
            
            [container_display]
            # 工作台升级在容器界面显示仓库的配置
            # 启用工作台升级后，以下容器界面将显示仓库界面
            # 默认值: 全部为 false
            
            # 切石机
            stonecutter = false
            # 制图台
            cartography_table = false
            # 锻造台
            smithing_table = false
            # 砂轮
            grindstone = false
            # 织布机
            loom = false
            # 熔炉
            furnace = false
            # 烟熏炉
            smoker = false
            # 高炉
            blast_furnace = false
            # 铁砧
            anvil = false
            # 附魔台
            enchanting_table = false
            # 酿造台
            brewing_stand = false
            # 信标
            beacon = false
            # 箱子
            chest = false
            # 木桶
            barrel = false
            # 末影箱
            ender_chest = false
            # 潜影盒
            shulker_box = false
            # 发射器
            dispenser = false
            # 投掷器
            dropper = false
            # 合成器
            crafter = false
            # 漏斗
            hopper = false
            # 陷阱箱
            trapped_chest = false
            # 漏斗矿车
            hopper_minecart = false
            # 运输矿车
            chest_minecart = false
            # 运输船
            chest_boat = false
            # 运输竹筏
            bamboo_chest_raft = false
            """;
        
        Files.writeString(configPath, defaultConfig);
        PortableStorage.LOGGER.info("已创建默认服务端配置文件: {}", configPath);
    }
    
    /**
     * 读取配置值
     */
    private void readConfigValues() {
        Config storageConfig = config.get("storage");
        if (storageConfig != null) {
            requireConditionToEnable = storageConfig.getOrElse("require_condition_to_enable", true);
            enableItem = storageConfig.getOrElse("enable_item", "minecraft:nether_star");
            consumeEnableItem = storageConfig.getOrElse("consume_enable_item", true);
            enableIncrementalSync = storageConfig.getOrElse("enable_incremental_sync", false);
            enableOnDemandSync = storageConfig.getOrElse("enable_on_demand_sync", true);
            incrementalSyncIntervalTicks = storageConfig.getOrElse("incremental_sync_interval_ticks", 2);
            incrementalSyncMaxEntries = storageConfig.getOrElse("incremental_sync_max_entries", 512);
            clearStorageOnEnable = storageConfig.getOrElse("clear_storage_on_enable", true);
            enableRiftFeature = storageConfig.getOrElse("enable_rift_feature", true);
            riftUpgradeItem = storageConfig.getOrElse("rift_upgrade_item", "block:minecraft:dragon_egg");
            riftSize = storageConfig.getOrElse("rift_size", 1);
            limitRiftHeight = storageConfig.getOrElse("limit_rift_height", false);
            enableVirtualCrafting = storageConfig.getOrElse("enable_virtual_crafting", true);
            enableInfiniteLava = storageConfig.getOrElse("enable_infinite_lava", true);
            enableInfiniteWater = storageConfig.getOrElse("enable_infinite_water", true);
            infiniteLavaThreshold = storageConfig.getOrElse("infinite_lava_threshold", 10000);
            infiniteWaterThreshold = storageConfig.getOrElse("infinite_water_threshold", 2);
            enableSizeLimit = storageConfig.getOrElse("enable_size_limit", true);
            Object sizeValue = storageConfig.get("max_storage_size_bytes");
            if (sizeValue instanceof Integer) {
                maxStorageSizeBytes = ((Integer) sizeValue).longValue();
            } else if (sizeValue instanceof Long) {
                maxStorageSizeBytes = (Long) sizeValue;
            } else {
                maxStorageSizeBytes = 102400L;
            }
        } else {
            PortableStorage.LOGGER.warn("配置文件中未找到 [storage] 部分，使用默认值");
        }
        
        Config containerConfig = config.get("container_display");
        if (containerConfig != null) {
            stonecutter = containerConfig.getOrElse("stonecutter", false);
            cartographyTable = containerConfig.getOrElse("cartography_table", false);
            smithingTable = containerConfig.getOrElse("smithing_table", false);
            grindstone = containerConfig.getOrElse("grindstone", false);
            loom = containerConfig.getOrElse("loom", false);
            furnace = containerConfig.getOrElse("furnace", false);
            smoker = containerConfig.getOrElse("smoker", false);
            blastFurnace = containerConfig.getOrElse("blast_furnace", false);
            anvil = containerConfig.getOrElse("anvil", false);
            enchantingTable = containerConfig.getOrElse("enchanting_table", false);
            brewingStand = containerConfig.getOrElse("brewing_stand", false);
            beacon = containerConfig.getOrElse("beacon", false);
            chest = containerConfig.getOrElse("chest", false);
            barrel = containerConfig.getOrElse("barrel", false);
            enderChest = containerConfig.getOrElse("ender_chest", false);
            shulkerBox = containerConfig.getOrElse("shulker_box", false);
            dispenser = containerConfig.getOrElse("dispenser", false);
            dropper = containerConfig.getOrElse("dropper", false);
            crafter = containerConfig.getOrElse("crafter", false);
            hopper = containerConfig.getOrElse("hopper", false);
            trappedChest = containerConfig.getOrElse("trapped_chest", false);
            hopperMinecart = containerConfig.getOrElse("hopper_minecart", false);
            chestMinecart = containerConfig.getOrElse("chest_minecart", false);
            chestBoat = containerConfig.getOrElse("chest_boat", false);
            bambooChestRaft = containerConfig.getOrElse("bamboo_chest_raft", false);
        } else {
            PortableStorage.LOGGER.warn("配置文件中未找到 [container_display] 部分，使用默认值");
        }
    }
    
    /**
     * 写入配置值
     */
    private void writeConfigValues() {
        Config storageConfig = config.get("storage");
        if (storageConfig == null) {
            storageConfig = config.createSubConfig();
            config.set("storage", storageConfig);
        }
        storageConfig.set("require_condition_to_enable", requireConditionToEnable);
        storageConfig.set("enable_item", enableItem);
        storageConfig.set("consume_enable_item", consumeEnableItem);
        storageConfig.set("enable_incremental_sync", enableIncrementalSync);
        storageConfig.set("enable_on_demand_sync", enableOnDemandSync);
        storageConfig.set("incremental_sync_interval_ticks", incrementalSyncIntervalTicks);
        storageConfig.set("incremental_sync_max_entries", incrementalSyncMaxEntries);
        storageConfig.set("clear_storage_on_enable", clearStorageOnEnable);
        storageConfig.set("enable_rift_feature", enableRiftFeature);
        storageConfig.set("rift_upgrade_item", riftUpgradeItem);
        storageConfig.set("rift_size", riftSize);
        storageConfig.set("limit_rift_height", limitRiftHeight);
        storageConfig.set("enable_virtual_crafting", enableVirtualCrafting);
        storageConfig.set("enable_infinite_lava", enableInfiniteLava);
        storageConfig.set("enable_infinite_water", enableInfiniteWater);
        storageConfig.set("infinite_lava_threshold", infiniteLavaThreshold);
        storageConfig.set("infinite_water_threshold", infiniteWaterThreshold);
        storageConfig.set("enable_size_limit", enableSizeLimit);
        storageConfig.set("max_storage_size_bytes", maxStorageSizeBytes);
        
        Config containerConfig = config.get("container_display");
        if (containerConfig == null) {
            containerConfig = config.createSubConfig();
            config.set("container_display", containerConfig);
        }
        containerConfig.set("stonecutter", stonecutter);
        containerConfig.set("cartography_table", cartographyTable);
        containerConfig.set("smithing_table", smithingTable);
        containerConfig.set("grindstone", grindstone);
        containerConfig.set("loom", loom);
        containerConfig.set("furnace", furnace);
        containerConfig.set("smoker", smoker);
        containerConfig.set("blast_furnace", blastFurnace);
        containerConfig.set("anvil", anvil);
        containerConfig.set("enchanting_table", enchantingTable);
        containerConfig.set("brewing_stand", brewingStand);
        containerConfig.set("beacon", beacon);
        containerConfig.set("chest", chest);
        containerConfig.set("barrel", barrel);
        containerConfig.set("ender_chest", enderChest);
        containerConfig.set("shulker_box", shulkerBox);
        containerConfig.set("dispenser", dispenser);
        containerConfig.set("dropper", dropper);
        containerConfig.set("crafter", crafter);
        containerConfig.set("hopper", hopper);
        containerConfig.set("trapped_chest", trappedChest);
        containerConfig.set("hopper_minecart", hopperMinecart);
        containerConfig.set("chest_minecart", chestMinecart);
        containerConfig.set("chest_boat", chestBoat);
        containerConfig.set("bamboo_chest_raft", bambooChestRaft);
    }

    /**
     * 确保配置文件包含所有必需键；若缺失则以默认值补全并返回是否有改动
     */
    private boolean ensureDefaultsWritten() {
        boolean changed = false;
        Config storageConfig = config.get("storage");
        if (storageConfig == null) {
            // 不在配置树中创建以避免 save() 重写文件；仅标记需要创建分节
            changed = true;
        }

        if (storageConfig == null || !storageConfig.contains("require_condition_to_enable")) {
            storageConfig.set("require_condition_to_enable", false);
            changed = true;
        }
        if (storageConfig == null || !storageConfig.contains("enable_item")) {
            storageConfig.set("enable_item", "minecraft:nether_star");
            changed = true;
        }
        if (storageConfig == null || !storageConfig.contains("consume_enable_item")) {
            storageConfig.set("consume_enable_item", true);
            changed = true;
        }
        if (storageConfig == null || !storageConfig.contains("enable_incremental_sync")) {
            storageConfig.set("enable_incremental_sync", false);
            changed = true;
        }
        if (storageConfig == null || !storageConfig.contains("enable_on_demand_sync")) {
            storageConfig.set("enable_on_demand_sync", true);
            changed = true;
        }
        if (storageConfig == null || !storageConfig.contains("incremental_sync_interval_ticks")) {
            storageConfig.set("incremental_sync_interval_ticks", 2);
            changed = true;
        }
        if (storageConfig == null || !storageConfig.contains("incremental_sync_max_entries")) {
            storageConfig.set("incremental_sync_max_entries", 512);
            changed = true;
        }

        Config containerConfig = config.get("container_display");
        if (containerConfig == null) {
            containerConfig = config.createSubConfig();
            config.set("container_display", containerConfig);
            changed = true;
        }

        // 检查所有容器配置项
        String[] containerKeys = {
            "stonecutter", "cartography_table", "smithing_table", "grindstone", "loom",
            "furnace", "smoker", "blast_furnace", "anvil", "enchanting_table",
            "brewing_stand", "beacon", "chest", "barrel", "ender_chest",
            "shulker_box", "dispenser", "dropper", "crafter", "hopper",
            "trapped_chest", "hopper_minecart", "chest_minecart", "chest_boat", "bamboo_chest_raft"
        };

        for (String key : containerKeys) {
            if (!containerConfig.contains(key)) {
                containerConfig.set(key, false);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * 仅以“文本追加”的方式，把缺失的分节/键与注释写回，不重写已有内容
     */
    private void writeMissingKeysWithCommentsTextually() {
        try {
            String content = Files.readString(configPath);
            String sectionHeader = "[storage]";

            int sectionStart = findSectionStart(content, sectionHeader);
            if (sectionStart < 0) {
                // 整个分节不存在：直接在文件末尾追加完整分节（含注释）
                String block = buildFullStorageSection();
                String sep = content.endsWith("\n") ? "" : System.lineSeparator();
                Files.writeString(configPath, content + sep + sep + block);
                return;
            }

            int sectionEnd = findSectionEnd(content, sectionStart);
            String section = content.substring(sectionStart, sectionEnd);

            StringBuilder toAppend = new StringBuilder();
            if (!containsKey(section, "require_condition_to_enable")) {
                toAppend.append(buildRequireConditionBlock());
            }
            if (!containsKey(section, "enable_item")) {
                toAppend.append(buildEnableItemBlock());
            }
            if (!containsKey(section, "consume_enable_item")) {
                toAppend.append(buildConsumeEnableBlock());
            }
            if (!containsKey(section, "enable_incremental_sync")) {
                toAppend.append(buildIncrementalSyncBlock());
            }
            if (!containsKey(section, "enable_on_demand_sync")) {
                toAppend.append(buildOnDemandSyncBlock());
            }
            
            // 检查容器配置部分
            int containerSectionStart = findSectionStart(content, "[container_display]");
            if (containerSectionStart < 0) {
                // 整个容器配置分节不存在：追加完整分节
                toAppend.append(System.lineSeparator()).append(buildFullContainerDisplaySection());
            } else {
                // 容器配置分节存在，检查缺失的键
                int containerSectionEnd = findSectionEnd(content, containerSectionStart);
                String containerSection = content.substring(containerSectionStart, containerSectionEnd);
                
                String[] containerKeys = {
                    "stonecutter", "cartography_table", "smithing_table", "grindstone", "loom",
                    "furnace", "smoker", "blast_furnace", "anvil", "enchanting_table",
                    "brewing_stand", "beacon", "chest", "barrel", "ender_chest",
                    "shulker_box", "dispenser", "dropper", "crafter", "hopper",
                    "trapped_chest", "hopper_minecart", "chest_minecart", "chest_boat", "bamboo_chest_raft"
                };
                
                for (String key : containerKeys) {
                    if (!containsKey(containerSection, key)) {
                        toAppend.append(buildContainerConfigBlock(key));
                    }
                }
            }

            if (toAppend.length() == 0) {
                return; // 无需追加
            }

            // 在分节尾部前插入（确保分节内部多一行空行再插入，便于阅读）
            String insertion = (section.endsWith("\n\n") ? "" : (section.endsWith("\n") ? "\n" : "\n\n"))
                + toAppend.toString();

            String newContent = content.substring(0, sectionEnd) + insertion + content.substring(sectionEnd);
            Files.writeString(configPath, newContent);
        } catch (IOException e) {
            PortableStorage.LOGGER.error("写回缺失配置项失败(文本插入)", e);
        }
    }

    private static int findSectionStart(String content, String header) {
        String[] lines = content.split("\n", -1);
        int pos = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(header)) {
                return pos;
            }
            pos += line.length() + 1; // include newline
        }
        return -1;
    }

    private static int findSectionEnd(String content, int sectionStart) {
        int nextHeader = content.indexOf("\n[", sectionStart + 1);
        return nextHeader >= 0 ? nextHeader : content.length();
    }

    private static boolean containsKey(String sectionContent, String key) {
        // 简单地在分节文本范围内查找以 key 开头的赋值行
        String regex = "(^|\n)" + java.util.regex.Pattern.quote(key) + "\s*=\s*";
        return java.util.regex.Pattern.compile(regex).matcher(sectionContent).find();
    }

    private static String buildFullStorageSection() {
        String ls = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("[storage]").append(ls);
        sb.append(buildRequireConditionBlock());
        sb.append(buildEnableItemBlock());
        sb.append(buildConsumeEnableBlock());
        sb.append(buildIncrementalSyncBlock());
        sb.append(buildOnDemandSyncBlock());
        sb.append(ls).append(buildFullContainerDisplaySection());
        return sb.toString();
    }

    private static String buildRequireConditionBlock() {
        String ls = System.lineSeparator();
        return "# 是否需要条件启用玩家随身仓库" + ls
            + "# 启用后玩家的随身仓库不再是默认就有的，而是需要达成条件才会有" + ls
            + "# 默认值: true" + ls
            + "require_condition_to_enable = true" + ls + ls;
    }

    private static String buildEnableItemBlock() {
        String ls = System.lineSeparator();
        return "# 启用玩家随身仓库的道具" + ls
            + "# 启用\"需要条件启用玩家随身仓库\"后用以启用玩家仓库的物品，手持右键以启用" + ls
            + "# 默认值: \"minecraft:nether_star\" (下界之星)" + ls
            + "# 格式: \"命名空间:物品ID\"" + ls
            + "enable_item = \"minecraft:nether_star\"" + ls + ls;
    }

    private static String buildConsumeEnableBlock() {
        String ls = System.lineSeparator();
        return "# 启用玩家随身仓库的道具是否消耗" + ls
            + "# 开启后启用玩家随身仓库时会消耗该物品一个" + ls
            + "# 默认值: true" + ls
            + "consume_enable_item = true" + ls + ls;
    }

    private static String buildIncrementalSyncBlock() {
        String ls = System.lineSeparator();
        return "# 是否启用增量同步" + ls
            + "# 启用后只同步发生变化的数据，减少网络传输量" + ls
            + "# 可能存在BUG未发现或未修复的错误" + ls
            + "# 默认值: false" + ls
            + "enable_incremental_sync = false" + ls + ls;
    }

    private static String buildOnDemandSyncBlock() {
        String ls = System.lineSeparator();
        return "# 是否启用按需同步" + ls
            + "# 启用后只在玩家查看仓库界面时才同步，否则积攒变化" + ls
            + "# 可以显著减少服务器负载，但可能导致数据延迟" + ls
            + "# 默认值: false" + ls
            + "enable_on_demand_sync = false" + ls + ls;
    }

    private static String buildFullContainerDisplaySection() {
        String ls = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("[container_display]").append(ls);
        sb.append("# 工作台升级在容器界面显示仓库的配置").append(ls);
        sb.append("# 启用工作台升级后，以下容器界面将显示仓库界面").append(ls);
        sb.append("# 默认值: 全部为 false").append(ls).append(ls);
        
        String[] containerConfigs = {
            "stonecutter", "切石机",
            "cartography_table", "制图台", 
            "smithing_table", "锻造台",
            "grindstone", "砂轮",
            "loom", "织布机",
            "furnace", "熔炉",
            "smoker", "烟熏炉",
            "blast_furnace", "高炉",
            "anvil", "铁砧",
            "enchanting_table", "附魔台",
            "brewing_stand", "酿造台",
            "beacon", "信标",
            "chest", "箱子",
            "barrel", "木桶",
            "ender_chest", "末影箱",
            "shulker_box", "潜影盒",
            "dispenser", "发射器",
            "dropper", "投掷器",
            "crafter", "合成器",
            "hopper", "漏斗",
            "trapped_chest", "陷阱箱",
            "hopper_minecart", "漏斗矿车",
            "chest_minecart", "运输矿车",
            "chest_boat", "运输船",
            "bamboo_chest_raft", "运输竹筏"
        };
        
        for (int i = 0; i < containerConfigs.length; i += 2) {
            String key = containerConfigs[i];
            String comment = containerConfigs[i + 1];
            sb.append("# ").append(comment).append(ls);
            sb.append(key).append(" = false").append(ls);
        }
        
        return sb.toString();
    }

    private static String buildContainerConfigBlock(String key) {
        String ls = System.lineSeparator();
        String comment = getContainerComment(key);
        return "# " + comment + ls + key + " = false" + ls;
    }

    private static String getContainerComment(String key) {
        switch (key) {
            case "stonecutter": return "切石机";
            case "cartography_table": return "制图台";
            case "smithing_table": return "锻造台";
            case "grindstone": return "砂轮";
            case "loom": return "织布机";
            case "furnace": return "熔炉";
            case "smoker": return "烟熏炉";
            case "blast_furnace": return "高炉";
            case "anvil": return "铁砧";
            case "enchanting_table": return "附魔台";
            case "brewing_stand": return "酿造台";
            case "beacon": return "信标";
            case "chest": return "箱子";
            case "barrel": return "木桶";
            case "ender_chest": return "末影箱";
            case "shulker_box": return "潜影盒";
            case "dispenser": return "发射器";
            case "dropper": return "投掷器";
            case "crafter": return "合成器";
            case "hopper": return "漏斗";
            case "trapped_chest": return "陷阱箱";
            case "hopper_minecart": return "漏斗矿车";
            case "chest_minecart": return "运输矿车";
            case "chest_boat": return "运输船";
            case "bamboo_chest_raft": return "运输竹筏";
            default: return key;
        }
    }
    
    /**
     * 设置默认值
     */
    private void setDefaultValues() {
        requireConditionToEnable = true;
        enableItem = "minecraft:nether_star";
        consumeEnableItem = true;
        enableIncrementalSync = false;
        enableOnDemandSync = true;
        enableInfiniteLava = true;
        enableInfiniteWater = true;
        infiniteLavaThreshold = 10000;
        infiniteWaterThreshold = 2;
        enableSizeLimit = true;
        maxStorageSizeBytes = 102400;
        
        // 容器配置默认值
        stonecutter = false;
        cartographyTable = false;
        smithingTable = false;
        grindstone = false;
        loom = false;
        furnace = false;
        smoker = false;
        blastFurnace = false;
        anvil = false;
        enchantingTable = false;
        brewingStand = false;
        beacon = false;
        chest = false;
        barrel = false;
        enderChest = false;
        shulkerBox = false;
        dispenser = false;
        dropper = false;
        crafter = false;
        hopper = false;
        trappedChest = false;
        hopperMinecart = false;
        chestMinecart = false;
        chestBoat = false;
        bambooChestRaft = false;
    }
    
    // Getter 方法
    public boolean isRequireConditionToEnable() {
        return requireConditionToEnable;
    }
    
    public String getEnableItem() {
        return enableItem;
    }
    
    public boolean isConsumeEnableItem() {
        return consumeEnableItem;
    }
    
    public boolean isEnableIncrementalSync() {
        return enableIncrementalSync;
    }
    
    public boolean isEnableOnDemandSync() {
        return enableOnDemandSync;
    }
    public int getIncrementalSyncIntervalTicks() { return incrementalSyncIntervalTicks; }
    public int getIncrementalSyncMaxEntries() { return incrementalSyncMaxEntries; }
    
    public boolean isClearStorageOnEnable() {
        return clearStorageOnEnable;
    }
    
    public boolean isEnableRiftFeature() {
        return enableRiftFeature;
    }
    
    public String getRiftUpgradeItem() {
        return riftUpgradeItem;
    }
    
    public int getRiftSize() {
        return riftSize;
    }
    
    public boolean isLimitRiftHeight() {
        return limitRiftHeight;
    }
    
    public boolean isEnableVirtualCrafting() {
        return enableVirtualCrafting;
    }
    
    public boolean isEnableInfiniteLava() {
        return enableInfiniteLava;
    }
    
    public boolean isEnableInfiniteWater() {
        return enableInfiniteWater;
    }
    
    public int getInfiniteLavaThreshold() {
        return infiniteLavaThreshold;
    }
    
    public int getInfiniteWaterThreshold() {
        return infiniteWaterThreshold;
    }
    
    public boolean isEnableSizeLimit() {
        return enableSizeLimit;
    }
    
    public long getMaxStorageSizeBytes() {
        return maxStorageSizeBytes;
    }
    
    // Setter 方法（用于运行时修改配置）
    public void setRequireConditionToEnable(boolean requireConditionToEnable) {
        this.requireConditionToEnable = requireConditionToEnable;
    }
    
    public void setEnableItem(String enableItem) {
        this.enableItem = enableItem;
    }
    
    public void setConsumeEnableItem(boolean consumeEnableItem) {
        this.consumeEnableItem = consumeEnableItem;
    }
    
    public void setEnableIncrementalSync(boolean enableIncrementalSync) {
        this.enableIncrementalSync = enableIncrementalSync;
    }
    
    public void setEnableOnDemandSync(boolean enableOnDemandSync) {
        this.enableOnDemandSync = enableOnDemandSync;
    }
    
    public void setRiftUpgradeItem(String riftUpgradeItem) {
        this.riftUpgradeItem = riftUpgradeItem;
    }
    
    public void setRiftSize(int riftSize) {
        this.riftSize = riftSize;
    }
    
    public void setEnableVirtualCrafting(boolean enableVirtualCrafting) {
        this.enableVirtualCrafting = enableVirtualCrafting;
    }
    
    public void setEnableInfiniteLava(boolean enableInfiniteLava) {
        this.enableInfiniteLava = enableInfiniteLava;
    }
    
    public void setEnableInfiniteWater(boolean enableInfiniteWater) {
        this.enableInfiniteWater = enableInfiniteWater;
    }
    
    public void setInfiniteLavaThreshold(int infiniteLavaThreshold) {
        this.infiniteLavaThreshold = infiniteLavaThreshold;
    }
    
    public void setInfiniteWaterThreshold(int infiniteWaterThreshold) {
        this.infiniteWaterThreshold = infiniteWaterThreshold;
    }
    
    public void setEnableSizeLimit(boolean enableSizeLimit) {
        this.enableSizeLimit = enableSizeLimit;
    }
    
    public void setMaxStorageSizeBytes(long maxStorageSizeBytes) {
        this.maxStorageSizeBytes = maxStorageSizeBytes;
    }
    
    // 容器配置 Getter 方法
    public boolean isStonecutter() { return stonecutter; }
    public boolean isCartographyTable() { return cartographyTable; }
    public boolean isSmithingTable() { return smithingTable; }
    public boolean isGrindstone() { return grindstone; }
    public boolean isLoom() { return loom; }
    public boolean isFurnace() { return furnace; }
    public boolean isSmoker() { return smoker; }
    public boolean isBlastFurnace() { return blastFurnace; }
    public boolean isAnvil() { return anvil; }
    public boolean isEnchantingTable() { return enchantingTable; }
    public boolean isBrewingStand() { return brewingStand; }
    public boolean isBeacon() { return beacon; }
    public boolean isChest() { return chest; }
    public boolean isBarrel() { return barrel; }
    public boolean isEnderChest() { return enderChest; }
    public boolean isShulkerBox() { return shulkerBox; }
    public boolean isDispenser() { return dispenser; }
    public boolean isDropper() { return dropper; }
    public boolean isCrafter() { return crafter; }
    public boolean isHopper() { return hopper; }
    public boolean isTrappedChest() { return trappedChest; }
    public boolean isHopperMinecart() { return hopperMinecart; }
    public boolean isChestMinecart() { return chestMinecart; }
    public boolean isChestBoat() { return chestBoat; }
    public boolean isBambooChestRaft() { return bambooChestRaft; }
    
    // 容器配置 Setter 方法
    public void setStonecutter(boolean stonecutter) { this.stonecutter = stonecutter; }
    public void setCartographyTable(boolean cartographyTable) { this.cartographyTable = cartographyTable; }
    public void setSmithingTable(boolean smithingTable) { this.smithingTable = smithingTable; }
    public void setGrindstone(boolean grindstone) { this.grindstone = grindstone; }
    public void setLoom(boolean loom) { this.loom = loom; }
    public void setFurnace(boolean furnace) { this.furnace = furnace; }
    public void setSmoker(boolean smoker) { this.smoker = smoker; }
    public void setBlastFurnace(boolean blastFurnace) { this.blastFurnace = blastFurnace; }
    public void setAnvil(boolean anvil) { this.anvil = anvil; }
    public void setEnchantingTable(boolean enchantingTable) { this.enchantingTable = enchantingTable; }
    public void setBrewingStand(boolean brewingStand) { this.brewingStand = brewingStand; }
    public void setBeacon(boolean beacon) { this.beacon = beacon; }
    public void setChest(boolean chest) { this.chest = chest; }
    public void setBarrel(boolean barrel) { this.barrel = barrel; }
    public void setEnderChest(boolean enderChest) { this.enderChest = enderChest; }
    public void setShulkerBox(boolean shulkerBox) { this.shulkerBox = shulkerBox; }
    public void setDispenser(boolean dispenser) { this.dispenser = dispenser; }
    public void setDropper(boolean dropper) { this.dropper = dropper; }
    public void setCrafter(boolean crafter) { this.crafter = crafter; }
    public void setHopper(boolean hopper) { this.hopper = hopper; }
    public void setTrappedChest(boolean trappedChest) { this.trappedChest = trappedChest; }
    public void setHopperMinecart(boolean hopperMinecart) { this.hopperMinecart = hopperMinecart; }
    public void setChestMinecart(boolean chestMinecart) { this.chestMinecart = chestMinecart; }
    public void setChestBoat(boolean chestBoat) { this.chestBoat = chestBoat; }
    public void setBambooChestRaft(boolean bambooChestRaft) { this.bambooChestRaft = bambooChestRaft; }
    
    /**
     * 根据容器标识符检查是否应该显示仓库
     */
    public boolean shouldShowStorageInContainer(String containerId) {
        if (containerId == null) return false;
        
        switch (containerId) {
            case "minecraft:stonecutter": return stonecutter;
            case "minecraft:cartography_table": return cartographyTable;
            case "minecraft:smithing_table": return smithingTable;
            case "minecraft:grindstone": return grindstone;
            case "minecraft:loom": return loom;
            case "minecraft:furnace": return furnace;
            case "minecraft:smoker": return smoker;
            case "minecraft:blast_furnace": return blastFurnace;
            case "minecraft:anvil": return anvil;
            case "minecraft:enchanting_table": return enchantingTable;
            case "minecraft:brewing_stand": return brewingStand;
            case "minecraft:beacon": return beacon;
            case "minecraft:chest": return chest;
            case "minecraft:barrel": return barrel;
            case "minecraft:ender_chest": return enderChest;
            case "minecraft:shulker_box": return shulkerBox;
            case "minecraft:dispenser": return dispenser;
            case "minecraft:dropper": return dropper;
            case "minecraft:crafter": return crafter;
            case "minecraft:hopper": return hopper;
            case "minecraft:trapped_chest": return trappedChest;
            case "minecraft:hopper_minecart": return hopperMinecart;
            case "minecraft:chest_minecart": return chestMinecart;
            case "minecraft:oak_chest_boat":
            case "minecraft:birch_chest_boat":
            case "minecraft:spruce_chest_boat":
            case "minecraft:jungle_chest_boat":
            case "minecraft:acacia_chest_boat":
            case "minecraft:dark_oak_chest_boat":
            case "minecraft:mangrove_chest_boat":
            case "minecraft:cherry_chest_boat":
                return chestBoat;
            case "minecraft:bamboo_chest_raft": return bambooChestRaft;
            default: return false;
        }
    }
}
