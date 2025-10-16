package com.portable.storage.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.portable.storage.PortableStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("portable-storage-client.json");
    
    private static ClientConfig INSTANCE = new ClientConfig();
    
    // 折叠状态
    public boolean collapsed = false;
    
    // 排序方案
    public SortMode sortMode = SortMode.COUNT;
    public boolean sortAscending = false; // false=降序，true=升序
    
    // 合成补充
    public boolean craftRefill = true;
    
    // 自动传入
    public boolean autoDeposit = false;

    // 智能折叠（按物品ID折叠不同 NBT 但同 ID 的物品）
    public boolean smartCollapse = false;

    // 搜索位置
    public SearchPos searchPos = SearchPos.BOTTOM; // 默认底部
    
    // 仓库位置
    public StoragePos storagePos = StoragePos.BOTTOM; // 默认底部
    
    // 虚拟合成显示状态
    public boolean virtualCraftingVisible = false; // 默认不显示
    
    // 最大可见行数
    public int maxVisibleRows = 6; // 默认最大6行
    
    // 收藏（星标）物品：以物品ID（namespace:id）标识，客户端本地配置
    public java.util.Set<String> favorites = new java.util.LinkedHashSet<>();
    
    // 拼音搜索支持
    public boolean pinyinSearch = true; // 默认启用拼音搜索
    
    // 多音字映射表：字符 -> 所有可能的拼音数组
    public java.util.Map<String, String[]> polyphoneMap = new java.util.HashMap<>();
    
    public enum SortMode {
        COUNT("count"),           // 数量
        NAME("name"),            // 物品名称
        MOD_ID("mod_id"),        // mod id
        UPDATE_TIME("update_time"); // 更新时间
        
        private final String key;
        
        SortMode(String key) {
            this.key = key;
        }
        
        public String getKey() {
            return key;
        }
        
        public SortMode next() {
            SortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum SearchPos {
        BOTTOM,    // 仓库UI下面
        TOP,       // 玩家背包上方（靠近）
        TOP2,      // 玩家背包上方（更靠上）
        MIDDLE;    // 插入在背包与仓库之间
        public SearchPos next() {
            SearchPos[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
    
    public enum StoragePos {
        BOTTOM,    // 仓库在底部（默认）
        TOP;       // 仓库在顶部
        public StoragePos next() {
            StoragePos[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
    
    public static ClientConfig getInstance() {
        return INSTANCE;
    }
    
    /**
     * 初始化默认多音字映射表
     */
    private void initializeDefaultPolyphoneMap() {
        polyphoneMap.clear();
        
        // 镐：gao, hao
        polyphoneMap.put("镐", new String[]{"gao", "hao"});
        
        // 可以根据需要继续添加其他多音字
        // polyphoneMap.put("行", new String[]{"hang", "xing"});
        // polyphoneMap.put("重", new String[]{"zhong", "chong"});
        // polyphoneMap.put("长", new String[]{"chang", "zhang"});
    }
    
    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                // 以默认实例为基准，按存在的键进行覆盖，从而保留默认值并补全缺项
                ClientConfig defaults = new ClientConfig();
                boolean changed = false;

                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                ClientConfig merged = new ClientConfig();

                // 先拷贝默认值
                merged.collapsed = defaults.collapsed;
                merged.sortMode = defaults.sortMode;
                merged.sortAscending = defaults.sortAscending;
                merged.craftRefill = defaults.craftRefill;
                merged.autoDeposit = defaults.autoDeposit;
                merged.smartCollapse = defaults.smartCollapse;
                merged.searchPos = defaults.searchPos;
                merged.storagePos = defaults.storagePos;
                merged.virtualCraftingVisible = defaults.virtualCraftingVisible;
                merged.maxVisibleRows = defaults.maxVisibleRows;
                merged.favorites = new java.util.LinkedHashSet<>(defaults.favorites);
                merged.pinyinSearch = defaults.pinyinSearch;
                merged.polyphoneMap = new java.util.HashMap<>(defaults.polyphoneMap);

                // 再按文件中存在的键覆盖
                if (obj.has("collapsed")) {
                    merged.collapsed = obj.get("collapsed").getAsBoolean();
                } else {
                    changed = true;
                }
                if (obj.has("sortMode")) {
                    try {
                        merged.sortMode = SortMode.valueOf(obj.get("sortMode").getAsString());
                    } catch (IllegalArgumentException ignored) { /* 保持默认 */ }
                } else {
                    changed = true;
                }
                if (obj.has("sortAscending")) {
                    merged.sortAscending = obj.get("sortAscending").getAsBoolean();
                } else {
                    changed = true;
                }
                if (obj.has("craftRefill")) {
                    merged.craftRefill = obj.get("craftRefill").getAsBoolean();
                } else {
                    changed = true;
                }
                if (obj.has("autoDeposit")) {
                    merged.autoDeposit = obj.get("autoDeposit").getAsBoolean();
                } else {
                    changed = true;
                }
                if (obj.has("smartCollapse")) {
                    merged.smartCollapse = obj.get("smartCollapse").getAsBoolean();
                } else {
                    changed = true;
                }
                if (obj.has("searchPos")) {
                    try {
                        merged.searchPos = SearchPos.valueOf(obj.get("searchPos").getAsString());
                    } catch (IllegalArgumentException ignored) { /* 保持默认 */ }
                } else {
                    changed = true;
                }
                if (obj.has("storagePos")) {
                    try {
                        merged.storagePos = StoragePos.valueOf(obj.get("storagePos").getAsString());
                    } catch (IllegalArgumentException ignored) { /* 保持默认 */ }
                } else {
                    changed = true;
                }
                if (obj.has("virtualCraftingVisible")) {
                    merged.virtualCraftingVisible = obj.get("virtualCraftingVisible").getAsBoolean();
                } else {
                    changed = true;
                }
                if (obj.has("maxVisibleRows")) {
                    merged.maxVisibleRows = obj.get("maxVisibleRows").getAsInt();
                } else {
                    changed = true;
                }
                // 读取收藏集合（如果存在）
                if (obj.has("favorites")) {
                    try {
                        JsonArray arr = obj.getAsJsonArray("favorites");
                        java.util.Set<String> fav = new java.util.LinkedHashSet<>();
                        for (int i = 0; i < arr.size(); i++) {
                            String s = arr.get(i).getAsString();
                            if (s != null && !s.isEmpty()) fav.add(s);
                        }
                        merged.favorites = fav;
                    } catch (Exception ignored) { /* 保持默认 */ }
                } else {
                    changed = true;
                }
                if (obj.has("pinyinSearch")) {
                    merged.pinyinSearch = obj.get("pinyinSearch").getAsBoolean();
                } else {
                    changed = true;
                }
                
                // 读取多音字映射表（如果存在）
                if (obj.has("polyphoneMap")) {
                    try {
                        JsonObject polyphoneObj = obj.getAsJsonObject("polyphoneMap");
                        java.util.Map<String, String[]> polyphoneMap = new java.util.HashMap<>();
                        
                        for (String key : polyphoneObj.keySet()) {
                            JsonArray pinyinArray = polyphoneObj.getAsJsonArray(key);
                            String[] pinyins = new String[pinyinArray.size()];
                            for (int i = 0; i < pinyinArray.size(); i++) {
                                pinyins[i] = pinyinArray.get(i).getAsString();
                            }
                            polyphoneMap.put(key, pinyins);
                        }
                        merged.polyphoneMap = polyphoneMap;
                    } catch (Exception ignored) { 
                        // 解析失败，使用默认值
                        merged.initializeDefaultPolyphoneMap();
                        changed = true;
                    }
                } else {
                    merged.initializeDefaultPolyphoneMap();
                    changed = true;
                }

                INSTANCE = merged;
                if (changed) {
                    save();
                }
                PortableStorage.LOGGER.info("Loaded client config from {} (merged defaults if missing)", CONFIG_PATH);
            } catch (IOException e) {
                PortableStorage.LOGGER.error("Failed to load client config", e);
                INSTANCE = new ClientConfig();
            }
        } else {
            INSTANCE = new ClientConfig();
            INSTANCE.initializeDefaultPolyphoneMap();
            save();
        }
    }
    
    public static void save() {
        try {
            String json = GSON.toJson(INSTANCE);
            Files.writeString(CONFIG_PATH, json);
            PortableStorage.LOGGER.debug("Saved client config to {}", CONFIG_PATH);
        } catch (IOException e) {
            PortableStorage.LOGGER.error("Failed to save client config", e);
        }
    }
    
    /**
     * 热重载配置文件
     * 用于在运行时重新加载配置，支持多音字映射表的动态更新
     */
    public static void reload() {
        PortableStorage.LOGGER.info("Reloading client config...");
        load();
        PortableStorage.LOGGER.info("Client config reloaded successfully");
    }
    
    /**
     * 添加多音字到映射表
     * 
     * @param character 字符
     * @param pinyins 所有可能的拼音
     */
    public void addPolyphone(String character, String... pinyins) {
        if (character != null && pinyins != null && pinyins.length > 0) {
            polyphoneMap.put(character, pinyins);
            save(); // 自动保存配置
            PortableStorage.LOGGER.info("Added polyphone: {} -> {}", character, java.util.Arrays.toString(pinyins));
        }
    }
    
    /**
     * 移除多音字
     * 
     * @param character 字符
     */
    public void removePolyphone(String character) {
        if (character != null && polyphoneMap.containsKey(character)) {
            polyphoneMap.remove(character);
            save(); // 自动保存配置
            PortableStorage.LOGGER.info("Removed polyphone: {}", character);
        }
    }
    
    /**
     * 获取所有多音字
     * 
     * @return 多音字集合
     */
    public java.util.Set<String> getAllPolyphones() {
        return new java.util.HashSet<>(polyphoneMap.keySet());
    }
}

