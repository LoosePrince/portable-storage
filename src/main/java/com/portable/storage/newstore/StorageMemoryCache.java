package com.portable.storage.newstore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.portable.storage.PortableStorage;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;

/**
 * 存储系统内存缓存管理器
 * 负责管理模板数据和玩家数据的内存缓存，以及定时保存
 */
public final class StorageMemoryCache {
    private static final int MAX_CACHE_SIZE = 1000; // 最大缓存玩家数量
    private static final long SAVE_INTERVAL_SECONDS = 30; // 30秒保存一次
    
    // 模板数据缓存（服务器启动时加载）
    private static volatile TemplateIndex templateIndex;
    private static volatile Map<String, ItemStack> templateCache = new ConcurrentHashMap<>();
    private static volatile boolean templateIndexDirty = false; // 标记模板索引是否需要保存
    
    // 玩家数据缓存（玩家上线时加载）
    static final Map<UUID, PlayerCacheEntry> playerCache = new ConcurrentHashMap<>();
    
    // 定时保存任务
    private static volatile ScheduledExecutorService saveScheduler;
    private static volatile boolean isInitialized = false;
    private static volatile MinecraftServer currentServer = null;
    
    /**
     * 玩家缓存条目
     */
    public static final class PlayerCacheEntry {
        public final Map<String, PlayerStore.Entry> entries = new LinkedHashMap<>();
        public Long sessionId;
        public volatile boolean dirty = false; // 标记是否需要保存
        public volatile long lastAccessTime = System.currentTimeMillis();
        
        public PlayerCacheEntry(Long sessionId) {
            this.sessionId = sessionId;
        }
    }
    
    private StorageMemoryCache() {}
    
    /**
     * 初始化内存缓存系统
     */
    public static synchronized void initialize(MinecraftServer server) {
        // 如果已经初始化，先清理缓存再重新初始化
        if (isInitialized) {
            PortableStorage.LOGGER.info("Reinitializing storage memory cache...");
            clearAllCaches();
        } else {
            PortableStorage.LOGGER.info("Initializing storage memory cache...");
        }
        
        // 保存服务器实例
        currentServer = server;
        
        // 加载模板数据到内存
        loadTemplatesToMemory(server);
        
        // 启动定时保存任务
        startPeriodicSave(server);
        
        isInitialized = true;
        PortableStorage.LOGGER.info("Storage memory cache initialized successfully");
    }
    
    /**
     * 关闭内存缓存系统
     */
    public static synchronized void shutdown() {
        if (!isInitialized) return;
        
        PortableStorage.LOGGER.info("Shutting down storage memory cache...");
        
        // 停止定时保存任务
        if (saveScheduler != null) {
            saveScheduler.shutdown();
            try {
                if (!saveScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    saveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                saveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 立即保存所有脏数据
        if (currentServer != null) {
            PortableStorage.LOGGER.info("Saving all dirty data before shutdown...");
            saveDirtyData(currentServer);
        } else {
            PortableStorage.LOGGER.warn("No server instance available for final save");
        }
        
        // 清理所有缓存数据
        clearAllCaches();
        
        isInitialized = false;
        PortableStorage.LOGGER.info("Storage memory cache shutdown completed");
    }
    
    /**
     * 清理所有缓存数据
     */
    private static void clearAllCaches() {
        // 清理模板缓存
        templateIndex = null;
        templateCache.clear();
        templateIndexDirty = false;
        
        // 清理玩家缓存
        playerCache.clear();
        
        // 清理服务器实例
        currentServer = null;
        
        PortableStorage.LOGGER.info("All caches cleared");
    }
    
    /**
     * 加载模板数据到内存（异步加载，避免阻塞主线程）
     */
    private static void loadTemplatesToMemory(MinecraftServer server) {
        try {
            // 先加载模板索引（这个比较快）
            templateIndex = TemplateIndex.load(server);
            
            // 异步预加载所有模板到内存，避免阻塞主线程
            templateCache.clear();
            
            // 在后台线程中加载模板
            Thread templateLoader = new Thread(() -> {
                try {
                    int loadedCount = 0;
                    for (String key : templateIndex.keys()) {
                        TemplateIndex.Entry entry = templateIndex.get(key);
                        if (entry != null) {
                            ItemStack template = TemplateSlices.getTemplate(() -> server, templateIndex, key, server.getRegistryManager());
                            if (!template.isEmpty()) {
                                templateCache.put(key, template);
                                loadedCount++;
                            }
                        }
                    }
                    PortableStorage.LOGGER.info("Asynchronously loaded {} templates to memory", loadedCount);
                } catch (Exception e) {
                    PortableStorage.LOGGER.error("Failed to load templates to memory asynchronously", e);
                }
            }, "TemplateLoader");
            
            templateLoader.setDaemon(true);
            templateLoader.start();
            
            PortableStorage.LOGGER.info("Started asynchronous template loading...");
        } catch (Exception e) {
            PortableStorage.LOGGER.error("Failed to initialize template loading", e);
        }
    }
    
    /**
     * 启动定时保存任务
     */
    private static void startPeriodicSave(MinecraftServer server) {
        saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StorageMemoryCache-Save");
            t.setDaemon(true);
            return t;
        });
        
        saveScheduler.scheduleWithFixedDelay(() -> {
            try {
                saveDirtyData(server);
            } catch (Exception e) {
                PortableStorage.LOGGER.error("Error in periodic save task", e);
            }
        }, SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * 获取玩家缓存条目（如果不存在则从文件加载）
     */
    public static PlayerCacheEntry getPlayerCache(UUID uuid, MinecraftServer server) {
        // 如果缓存系统未初始化，返回空缓存
        if (!isInitialized) {
            return new PlayerCacheEntry(null);
        }
        
        PlayerCacheEntry entry = playerCache.get(uuid);
        if (entry == null) {
            // 从文件加载玩家数据
            entry = loadPlayerFromFile(server, uuid);
            if (entry != null) {
                // 检查缓存大小，如果超过限制则清理最久未访问的条目
                if (playerCache.size() >= MAX_CACHE_SIZE) {
                    cleanupOldestEntries();
                }
                playerCache.put(uuid, entry);
            }
        } else {
            // 更新访问时间
            entry.lastAccessTime = System.currentTimeMillis();
        }
        return entry;
    }
    
    /**
     * 从文件加载玩家数据
     */
    private static PlayerCacheEntry loadPlayerFromFile(MinecraftServer server, UUID uuid) {
        try {
            StoragePaths.ensureDirectories(server);
            Path file = StoragePaths.getPlayerFile(server, uuid);
            Map<String, PlayerStore.Entry> entries = new LinkedHashMap<>();
            if (!Files.exists(file)) return new PlayerCacheEntry(null);
            
        NbtCompound root = net.minecraft.nbt.NbtIo.readCompressed(file.toFile());
            if (root == null) return new PlayerCacheEntry(null);
            
            if (root.contains(PlayerStore.ENTRIES, net.minecraft.nbt.NbtElement.LIST_TYPE)) {
                NbtList list = root.getList(PlayerStore.ENTRIES, net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound c = list.getCompound(i);
                    PlayerStore.Entry e = new PlayerStore.Entry();
                    e.key = c.getString("key");
                    e.count = c.getLong("count");
                    e.ts = c.getLong("ts");
                    if (e.key != null && !e.key.isEmpty() && e.count > 0) {
                        entries.put(e.key, e);
                    }
                }
            }
            
            PlayerCacheEntry entry = new PlayerCacheEntry(null); // sessionId 暂时设为 null
            entry.entries.putAll(entries);
            return entry;
        } catch (Exception e) {
            PortableStorage.LOGGER.error("Failed to load player data for UUID: " + uuid, e);
            return null;
        }
    }
    
    /**
     * 清理最久未访问的缓存条目
     */
    private static void cleanupOldestEntries() {
        if (playerCache.size() < MAX_CACHE_SIZE) return;
        
        // 找到最久未访问的条目
        UUID oldestUuid = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<UUID, PlayerCacheEntry> entry : playerCache.entrySet()) {
            if (entry.getValue().lastAccessTime < oldestTime) {
                oldestTime = entry.getValue().lastAccessTime;
                oldestUuid = entry.getKey();
            }
        }
        
        if (oldestUuid != null) {
            PlayerCacheEntry entry = playerCache.remove(oldestUuid);
            if (entry != null && entry.dirty) {
                // 如果条目是脏的，需要先保存
                PortableStorage.LOGGER.warn("Evicting dirty player cache entry for UUID: " + oldestUuid);
            }
        }
    }
    
    /**
     * 保存脏数据
     */
    private static void saveDirtyData(MinecraftServer server) {
        int savedCount = 0;
        
        // 保存玩家数据
        for (Map.Entry<UUID, PlayerCacheEntry> entry : playerCache.entrySet()) {
            if (entry.getValue().dirty) {
                try {
                    // 直接写入文件，绕过内存缓存
                    writePlayerToFile(server, entry.getKey(), entry.getValue());
                    entry.getValue().dirty = false;
                    savedCount++;
                } catch (Exception e) {
                    PortableStorage.LOGGER.error("Failed to save player data for UUID: " + entry.getKey(), e);
                }
            }
        }
        
        // 保存模板索引和模板文件
        if (templateIndexDirty && templateIndex != null) {
            try {
                // 保存模板索引
                templateIndex.save(server);
                
                // 保存所有模板文件
                for (Map.Entry<String, ItemStack> templateEntry : templateCache.entrySet()) {
                    String key = templateEntry.getKey();
                    ItemStack stack = templateEntry.getValue();
                    if (stack != null && !stack.isEmpty()) {
                        // 查找模板条目
                        TemplateIndex.Entry entry = templateIndex.find(key);
                        if (entry != null) {
                            // 保存模板到文件
                            TemplateSlices.putTemplate(() -> server, templateIndex, key, stack, server.getRegistryManager());
                        }
                    }
                }
                
                templateIndexDirty = false;
                savedCount++;
            } catch (Exception e) {
                PortableStorage.LOGGER.error("Failed to save template data", e);
            }
        }
        
        if (savedCount > 0) {
            PortableStorage.LOGGER.debug("Saved {} dirty cache entries", savedCount);
        }
    }
    
    /**
     * 将玩家数据写入文件
     */
    private static void writePlayerToFile(MinecraftServer server, UUID uuid, PlayerCacheEntry entry) {
        try {
            StoragePaths.ensureDirectories(server);
            Path file = StoragePaths.getPlayerFile(server, uuid);
            NbtCompound root = new NbtCompound();
            if (entry.sessionId != null) root.putLong(PlayerStore.SESSION, entry.sessionId);
            NbtList list = new NbtList();
            for (PlayerStore.Entry e : entry.entries.values()) {
                if (e.count <= 0) continue;
                NbtCompound c = new NbtCompound();
                c.putString("key", e.key);
                c.putLong("count", e.count);
                c.putLong("ts", e.ts);
                list.add(c);
            }
            root.put(PlayerStore.ENTRIES, list);
            com.portable.storage.util.SafeNbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write player data to file", e);
        }
    }
    
    
    /**
     * 标记玩家数据为脏
     */
    public static void markPlayerDirty(UUID uuid) {
        PlayerCacheEntry entry = playerCache.get(uuid);
        if (entry != null) {
            entry.dirty = true;
        }
    }
    
    /**
     * 标记模板索引为脏
     */
    public static void markTemplateIndexDirty() {
        templateIndexDirty = true;
    }
    
    /**
     * 获取模板索引
     */
    public static TemplateIndex getTemplateIndex() {
        if (!isInitialized) {
            return new TemplateIndex();
        }
        return templateIndex;
    }
    
    /**
     * 获取模板缓存
     */
    public static Map<String, ItemStack> getTemplateCache() {
        if (!isInitialized) {
            return new ConcurrentHashMap<>();
        }
        return templateCache;
    }
    
    /**
     * 添加模板到缓存
     */
    public static void addTemplate(String key, ItemStack template) {
        templateCache.put(key, template);
    }
    
    /**
     * 从模板缓存中移除指定条目
     */
    public static void removeTemplateFromCache(String key) {
        templateCache.remove(key);
    }
    
    /**
     * 从缓存中移除玩家数据
     */
    public static void removePlayerCache(UUID uuid) {
        playerCache.remove(uuid);
    }
    
    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return String.format("Player cache: %d entries, Template cache: %d entries", 
            playerCache.size(), templateCache.size());
    }
}
