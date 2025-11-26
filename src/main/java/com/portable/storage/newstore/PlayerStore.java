package com.portable.storage.newstore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;

import com.portable.storage.PortableStorage;
import com.portable.storage.util.SafeNbtIo;

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
    
    /**
     * 清理所有玩家（在线+离线）中指定 key 的存量，用于损坏模板的自修。
     */
    public static void purgeKeyFromAllPlayers(MinecraftServer server, String key) {
        if (server == null || key == null || key.isEmpty()) return;
        
        // 在线/缓存玩家
        for (Map.Entry<UUID, StorageMemoryCache.PlayerCacheEntry> entry : StorageMemoryCache.playerCache.entrySet()) {
            StorageMemoryCache.PlayerCacheEntry cacheEntry = entry.getValue();
            if (cacheEntry.entries.remove(key) != null) {
                cacheEntry.dirty = true;
            }
        }
        
        // 离线玩家文件
        StoragePaths.ensureDirectories(server);
        Path playersDir = StoragePaths.getPlayersDir(server);
        if (!Files.exists(playersDir)) return;
        try (var stream = Files.list(playersDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> purgeKeyFromFile(path, key));
        } catch (IOException e) {
            PortableStorage.LOGGER.warn("清理由于损坏模板导致的玩家条目失败: {}", e.getMessage());
        }
    }
    
    private static void purgeKeyFromFile(Path file, String key) {
        try {
            NbtCompound root = SafeNbtIo.readCompressed(file);
            if (root == null || !root.contains(ENTRIES, NbtElement.LIST_TYPE)) return;
            NbtList list = root.getList(ENTRIES, NbtElement.COMPOUND_TYPE);
            boolean changed = false;
            for (int i = list.size() - 1; i >= 0; i--) {
                NbtCompound entry = list.getCompound(i);
                if (key.equals(entry.getString("key"))) {
                    list.remove(i);
                    changed = true;
                }
            }
            if (changed) {
                root.put(ENTRIES, list);
                SafeNbtIo.writeCompressed(root, file);
            }
        } catch (IOException ignored) {}
    }
}


