package com.portable.storage.newstore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/**
 * 玩家侧计数存储：players/{uuid}.nbt
 * 结构：
 * - sessionId: long (optional)
 * - entries: List( { key:string, count:long, ts:long } )
 * 
 * 现在使用内存缓存，所有操作都在内存中进行，定时保存到文件
 */
public final class PlayerStore {
    public static final String SESSION = "sessionId";
    public static final String ENTRIES = "entries";
    

    public static final class Entry {
        public String key;
        public long count;
        public long ts;
    }

    private PlayerStore() {}
    

    public static Map<String, Entry> readAll(MinecraftServer server, UUID uuid) {
        // 使用内存缓存，直接从缓存中读取
        StorageMemoryCache.PlayerCacheEntry cacheEntry = StorageMemoryCache.getPlayerCache(uuid, server);
        if (cacheEntry == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(cacheEntry.entries);
    }

    public static void writeAll(MinecraftServer server, UUID uuid, Map<String, Entry> entries, Long sessionId) {
        // 使用内存缓存，更新缓存中的数据
        StorageMemoryCache.PlayerCacheEntry cacheEntry = StorageMemoryCache.getPlayerCache(uuid, server);
        if (cacheEntry == null) {
            cacheEntry = new StorageMemoryCache.PlayerCacheEntry(sessionId);
        }
        
        // 更新缓存
        cacheEntry.entries.clear();
        cacheEntry.entries.putAll(entries);
        cacheEntry.sessionId = sessionId;
        cacheEntry.dirty = true; // 标记为脏数据
        
        // 将更新后的缓存条目放回缓存
        StorageMemoryCache.playerCache.put(uuid, cacheEntry);
    }

    public static void add(MinecraftServer server, UUID uuid, String key, long delta, long now) {
        if (delta <= 0) return;
        
        // 使用内存缓存，直接在缓存中操作
        StorageMemoryCache.PlayerCacheEntry cacheEntry = StorageMemoryCache.getPlayerCache(uuid, server);
        if (cacheEntry == null) {
            cacheEntry = new StorageMemoryCache.PlayerCacheEntry(null);
        }
        
        Entry e = cacheEntry.entries.computeIfAbsent(key, k -> new Entry());
        e.key = key;
        e.count = Math.max(0, e.count + delta);
        e.ts = now;
        
        // 标记为脏数据
        cacheEntry.dirty = true;
        
        // 将更新后的缓存条目放回缓存
        StorageMemoryCache.playerCache.put(uuid, cacheEntry);
    }

    public static long remove(MinecraftServer server, UUID uuid, String key, long delta, long now) {
        if (delta <= 0) return 0;
        
        // 使用内存缓存，直接在缓存中操作
        StorageMemoryCache.PlayerCacheEntry cacheEntry = StorageMemoryCache.getPlayerCache(uuid, server);
        if (cacheEntry == null) {
            return 0;
        }
        
        Entry e = cacheEntry.entries.get(key);
        if (e == null || e.count <= 0) return 0;
        
        long take = Math.min(delta, e.count);
        e.count -= take;
        e.ts = now;
        
        if (e.count <= 0) {
            cacheEntry.entries.remove(key);
        }
        
        // 标记为脏数据
        cacheEntry.dirty = true;
        
        // 将更新后的缓存条目放回缓存
        StorageMemoryCache.playerCache.put(uuid, cacheEntry);
        
        return take;
    }
}


