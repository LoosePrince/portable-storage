package com.portable.storage.newstore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import com.portable.storage.util.SafeNbtIo;

/**
 * 模板索引：key -> { slice:int, ref:long(optional), size:int(optional) }
 */
public final class TemplateIndex {
    public static final String ROOT = "map";

    public static final class Entry {
        public int slice;
        public long ref; // 可选引用计数，-1 表示未知
        public int size; // 可选模板字节估计
    }

    private final Map<String, Entry> map = new HashMap<>();
    private int currentMaxSlice = 1;

    public static TemplateIndex load(MinecraftServer server) {
        StoragePaths.ensureDirectories(server);
        Path file = StoragePaths.getIndexFile(server);
        TemplateIndex idx = new TemplateIndex();
        if (!Files.exists(file)) return idx;
        try {
        NbtCompound root = SafeNbtIo.readCompressed(file);
            if (root != null && root.contains(ROOT)) {
                NbtCompound m = root.getCompound(ROOT);
                for (String k : m.getKeys()) {
                    NbtCompound v = m.getCompound(k);
                    Entry e = new Entry();
                    e.slice = v.getInt("slice");
                    e.ref = v.contains("ref") ? v.getLong("ref") : -1L;
                    e.size = v.contains("size") ? v.getInt("size") : 0;
                    idx.map.put(k, e);
                    if (e.slice > idx.currentMaxSlice) idx.currentMaxSlice = e.slice;
                }
            }
        } catch (IOException ignored) {}
        return idx;
    }

    public void save(MinecraftServer server) {
        StoragePaths.ensureDirectories(server);
        Path file = StoragePaths.getIndexFile(server);
        NbtCompound root = new NbtCompound();
        NbtCompound m = new NbtCompound();
        for (Map.Entry<String, Entry> en : map.entrySet()) {
            NbtCompound v = new NbtCompound();
            v.putInt("slice", Math.max(1, en.getValue().slice));
            if (en.getValue().ref >= 0) v.putLong("ref", en.getValue().ref);
            if (en.getValue().size > 0) v.putInt("size", en.getValue().size);
            m.put(en.getKey(), v);
        }
        root.put(ROOT, m);
        try {
            SafeNbtIo.writeCompressed(root, file);
        } catch (IOException ignored) {}
    }

    public Entry find(String key) {
        return map.get(key);
    }

    public void put(String key, int slice, int sizeEstimate) {
        Entry e = map.computeIfAbsent(key, k -> new Entry());
        e.slice = Math.max(1, slice);
        if (sizeEstimate > 0) e.size = sizeEstimate;
        if (e.ref < 0) e.ref = 0;
    }

    public void remove(String key) {
        map.remove(key);
    }

    public void incRef(String key, long delta) {
        Entry e = map.get(key);
        if (e != null) {
            if (e.ref < 0) e.ref = 0;
            e.ref += delta;
            if (e.ref < 0) e.ref = 0;
        }
    }

    public int getOrAllocateSlice() {
        // 简化：直接返回 currentMaxSlice，具体滚动由 TemplateSlices 决定。
        return Math.max(1, currentMaxSlice);
    }

    public void rollToNextSlice() {
        currentMaxSlice = Math.max(1, currentMaxSlice + 1);
    }

    // ===== 辅助访问器（供重建器/维护工具使用） =====
    public java.util.Set<String> keys() { return map.keySet(); }
    public Entry get(String key) { return map.get(key); }
}


