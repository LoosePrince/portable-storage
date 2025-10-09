package com.portable.storage.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
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
    private boolean enableIncrementalSync = false;
    
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
            # 可能存在BUG未发现或未修复的错误
            # 默认值: false
            enable_incremental_sync = false
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
            enableIncrementalSync = storageConfig.getOrElse("enable_incremental_sync", false);
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
        return sb.toString();
    }

    private static String buildRequireConditionBlock() {
        String ls = System.lineSeparator();
        return "# 是否需要条件启用玩家随身仓库" + ls
            + "# 启用后玩家的随身仓库不再是默认就有的，而是需要达成条件才会有" + ls
            + "# 默认值: false" + ls
            + "require_condition_to_enable = false" + ls + ls;
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
    
    /**
     * 设置默认值
     */
    private void setDefaultValues() {
        requireConditionToEnable = false;
        enableItem = "minecraft:nether_star";
        consumeEnableItem = true;
        enableIncrementalSync = false;
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
