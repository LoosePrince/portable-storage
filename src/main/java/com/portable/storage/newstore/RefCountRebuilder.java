package com.portable.storage.newstore;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 扫描 players/ 重建 index.nbt 的 ref 计数；可选清理 ref==0 的模板与索引项。
 */
public final class RefCountRebuilder {
    private RefCountRebuilder() {}

    public static void rebuild(MinecraftServer server, boolean cleanupZeroRefs) {
        StoragePaths.ensureDirectories(server);
        TemplateIndex index = TemplateIndex.load(server);

        // 累计所有玩家文件中的 key 计数
        Map<String, Long> totals = new HashMap<>();
        Path playersDir = StoragePaths.getPlayersDir(server);
        try (var stream = Files.list(playersDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".nbt")).forEach(file -> {
                try {
                    NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
                    if (root == null) return;
                    if (!root.contains(PlayerStore.ENTRIES, NbtElement.LIST_TYPE)) return;
                    NbtList list = root.getList(PlayerStore.ENTRIES, NbtElement.COMPOUND_TYPE);
                    for (int i = 0; i < list.size(); i++) {
                        NbtCompound c = list.getCompound(i);
                        String key = c.getString("key");
                        long count = c.getLong("count");
                        if (key == null || key.isEmpty() || count <= 0) continue;
                        totals.merge(key, count, Long::sum);
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}

        // 写回 ref
        for (String k : index.keys()) {
            TemplateIndex.Entry e = index.get(k);
            long ref = totals.getOrDefault(k, 0L);
            e.ref = Math.max(0L, ref);
        }

        if (cleanupZeroRefs) {
            java.util.ArrayList<String> toRemove = new java.util.ArrayList<>();
            for (String k : index.keys()) {
                TemplateIndex.Entry e = index.get(k);
                if (e != null && e.ref <= 0) {
                    TemplateSlices.removeTemplate(() -> server, index, k);
                    toRemove.add(k);
                }
            }
            for (String k : toRemove) index.remove(k);
        }

        index.save(server);
    }
}


