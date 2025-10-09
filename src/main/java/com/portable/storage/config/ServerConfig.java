package com.portable.storage.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.portable.storage.PortableStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 服务端配置管理器
 * 负责加载、解析和保存服务端配置文件
 */
public class ServerConfig {
    private static final String CONFIG_FILE_NAME = "portable-storage-server.toml";
    private static ServerConfig INSTANCE = new ServerConfig();
    
    // 配置项
    private boolean requireConditionToEnable = false;
    private String enableItem = "minecraft:nether_star";
    private boolean consumeEnableItem = true;
    private boolean enableIncrementalSync = true;
    
    private FileConfig config;
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
            
            // 加载配置文件
            config = FileConfig.of(configPath);
            config.load();
            
            // 读取配置值
            readConfigValues();
            
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
            # 默认值: false
            require_condition_to_enable = false
            
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
            # 默认值: true
            enable_incremental_sync = true
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
            requireConditionToEnable = storageConfig.getOrElse("require_condition_to_enable", false);
            enableItem = storageConfig.getOrElse("enable_item", "minecraft:nether_star");
            consumeEnableItem = storageConfig.getOrElse("consume_enable_item", true);
            enableIncrementalSync = storageConfig.getOrElse("enable_incremental_sync", true);
        } else {
            PortableStorage.LOGGER.warn("配置文件中未找到 [storage] 部分，使用默认值");
            setDefaultValues();
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
    }
    
    /**
     * 设置默认值
     */
    private void setDefaultValues() {
        requireConditionToEnable = false;
        enableItem = "minecraft:nether_star";
        consumeEnableItem = true;
        enableIncrementalSync = true;
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
}
