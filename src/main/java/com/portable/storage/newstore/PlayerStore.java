package com.portable.storage.newstore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;

/**
 * 玩家侧计数存储：players/{uuid}.nbt
 * 结构：
 * - sessionId: long (optional)
 * - entries: List( { key:string, count:long, ts:long } )
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
        StoragePaths.ensureDirectories(server);
        Path file = StoragePaths.getPlayerFile(server, uuid);
        Map<String, Entry> map = new LinkedHashMap<>();
        if (!Files.exists(file)) return map;
        try {
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            if (root == null) return map;
            if (root.contains(ENTRIES, NbtElement.LIST_TYPE)) {
                NbtList list = root.getList(ENTRIES, NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound c = list.getCompound(i);
                    Entry e = new Entry();
                    e.key = c.getString("key");
                    e.count = c.getLong("count");
                    e.ts = c.getLong("ts");
                    if (e.key != null && !e.key.isEmpty() && e.count > 0) {
                        map.put(e.key, e);
                    }
                }
            }
        } catch (IOException ignored) {}
        return map;
    }

    public static void writeAll(MinecraftServer server, UUID uuid, Map<String, Entry> entries, Long sessionId) {
        StoragePaths.ensureDirectories(server);
        Path file = StoragePaths.getPlayerFile(server, uuid);
        NbtCompound root = new NbtCompound();
        if (sessionId != null) root.putLong(SESSION, sessionId);
        NbtList list = new NbtList();
        for (Entry e : entries.values()) {
            if (e.count <= 0) continue;
            NbtCompound c = new NbtCompound();
            c.putString("key", e.key);
            c.putLong("count", e.count);
            c.putLong("ts", e.ts);
            list.add(c);
        }
        root.put(ENTRIES, list);
        try {
            NbtIo.writeCompressed(root, file);
        } catch (IOException ignored) {}
    }

    public static void add(MinecraftServer server, UUID uuid, String key, long delta, long now) {
        if (delta <= 0) return;
        Map<String, Entry> map = readAll(server, uuid);
        Entry e = map.computeIfAbsent(key, k -> new Entry());
        e.key = key;
        e.count = Math.max(0, e.count + delta);
        e.ts = now;
        writeAll(server, uuid, map, null);
    }

    public static long remove(MinecraftServer server, UUID uuid, String key, long delta, long now) {
        if (delta <= 0) return 0;
        Map<String, Entry> map = readAll(server, uuid);
        Entry e = map.get(key);
        if (e == null || e.count <= 0) return 0;
        long take = Math.min(delta, e.count);
        e.count -= take;
        e.ts = now;
        if (e.count <= 0) map.remove(key);
        writeAll(server, uuid, map, null);
        return take;
    }
}


