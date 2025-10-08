package com.portable.storage.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    
    public static ClientConfig getInstance() {
        return INSTANCE;
    }
    
    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, ClientConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new ClientConfig();
                }
                PortableStorage.LOGGER.info("Loaded client config from {}", CONFIG_PATH);
            } catch (IOException e) {
                PortableStorage.LOGGER.error("Failed to load client config", e);
                INSTANCE = new ClientConfig();
            }
        } else {
            INSTANCE = new ClientConfig();
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
}

