package com.portable.storage.newstore;

import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 新版存储的公共服务：对外部自动化与交互提供统一的“存入新版存储”入口。
 */
public final class NewStoreService {
    private NewStoreService() {}

    public static void insertForOnlinePlayer(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        StoragePaths.ensureDirectories(server);
        TemplateIndex index = TemplateIndex.load(server);
        String key = ItemKeyHasher.hash(stack, player.getRegistryManager());
        if (key == null || key.isEmpty()) return;
        if (index.find(key) == null) {
            TemplateSlices.putTemplate(() -> server, index, key, stack.copy(), player.getRegistryManager());
        }
        long now = System.currentTimeMillis();
        PlayerStore.add(server, player.getUuid(), key, stack.getCount(), now);
        index.incRef(key, stack.getCount());
        index.save(server);
        com.portable.storage.net.ServerNetworkingHandlers.sendSync(player);
    }

    public static void insertForOfflineUuid(MinecraftServer server, java.util.UUID uuid, ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (server == null || uuid == null || stack == null || stack.isEmpty()) return;
        StoragePaths.ensureDirectories(server);
        TemplateIndex index = TemplateIndex.load(server);
        String key = ItemKeyHasher.hash(stack, lookup);
        if (key == null || key.isEmpty()) return;
        if (index.find(key) == null) {
            TemplateSlices.putTemplate(() -> server, index, key, stack.copy(), lookup);
        }
        long now = System.currentTimeMillis();
        PlayerStore.add(server, uuid, key, stack.getCount(), now);
        index.incRef(key, stack.getCount());
        index.save(server);
    }

    public static long takeForOnlinePlayer(ServerPlayerEntity player, ItemStack variant, int want) {
        if (player == null || variant == null || variant.isEmpty() || want <= 0) return 0;
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        String key = ItemKeyHasher.hash(variant, player.getRegistryManager());
        if (key == null || key.isEmpty()) return 0;
        long taken = PlayerStore.remove(server, player.getUuid(), key, want, System.currentTimeMillis());
        if (taken > 0) {
            TemplateIndex index = TemplateIndex.load(server);
            index.incRef(key, -taken);
            if (index.find(key) != null && index.find(key).ref <= 0) {
                TemplateSlices.removeTemplate(() -> server, index, key);
            }
            index.save(server);
            com.portable.storage.net.ServerNetworkingHandlers.sendSync(player);
        }
        return taken;
    }

    /**
     * 从新版存储中提取物品（用于绑定木桶等自动化提取）
     */
    public static ItemStack takeFromNewStore(MinecraftServer server, java.util.UUID uuid, ItemStack variant, int want) {
        if (server == null || uuid == null || variant == null || variant.isEmpty() || want <= 0) return ItemStack.EMPTY;
        String key = ItemKeyHasher.hash(variant, null);
        if (key == null || key.isEmpty()) return ItemStack.EMPTY;
        long taken = PlayerStore.remove(server, uuid, key, want, System.currentTimeMillis());
        if (taken > 0) {
            TemplateIndex index = TemplateIndex.load(server);
            index.incRef(key, -taken);
            if (index.find(key) != null && index.find(key).ref <= 0) {
                TemplateSlices.removeTemplate(() -> server, index, key);
            }
            index.save(server);
            ItemStack result = variant.copy();
            result.setCount((int) Math.min(variant.getMaxCount(), taken));
            return result;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 构建共享视图：合并所有相关玩家的旧版+新版存储
     */
    public static com.portable.storage.storage.StorageInventory buildSharedView(MinecraftServer server, java.util.UUID viewerUuid, java.util.Set<java.util.UUID> sharedUuids) {
        com.portable.storage.storage.StorageInventory agg = new com.portable.storage.storage.StorageInventory(0);
        if (server == null || sharedUuids == null || sharedUuids.isEmpty()) return agg;
        
        // 1) 先合并旧版存储
        for (java.util.UUID uuid : sharedUuids) {
            var legacy = com.portable.storage.player.StoragePersistence.loadStorage(server, uuid);
            for (int i = 0; i < legacy.getCapacity(); i++) {
                ItemStack disp = legacy.getDisplayStack(i);
                if (disp.isEmpty()) continue;
                long cnt = legacy.getCountByIndex(i);
                if (cnt <= 0) continue;
                long left = cnt;
                while (left > 0) {
                    int chunk = (int) Math.min(Integer.MAX_VALUE, left);
                    ItemStack copy = disp.copy();
                    copy.setCount(chunk);
                    agg.insertItemStackWithOriginalTimestamp(copy, legacy.getTimestampByIndex(i));
                    left -= chunk;
                }
            }
        }
        
        // 2) 再合并新版存储
        TemplateIndex index = TemplateIndex.load(server);
        for (java.util.UUID uuid : sharedUuids) {
            var entries = PlayerStore.readAll(server, uuid);
            for (com.portable.storage.newstore.PlayerStore.Entry e : entries.values()) {
                if (e.count <= 0) continue;
                ItemStack stack = TemplateSlices.getTemplate(() -> server, index, e.key, null);
                if (stack.isEmpty()) continue;
                long left = e.count;
                while (left > 0) {
                    int chunk = (int) Math.min(Integer.MAX_VALUE, left);
                    ItemStack copy = stack.copy();
                    copy.setCount(chunk);
                    agg.insertItemStackWithOriginalTimestamp(copy, e.ts);
                    left -= chunk;
                }
            }
        }
        return agg;
    }

    /**
     * 从共享视图中提取物品（按优先级：旧版 -> 新版）
     */
    public static long takeFromSharedView(MinecraftServer server, java.util.UUID viewerUuid, java.util.Set<java.util.UUID> sharedUuids, ItemStack variant, int want) {
        if (server == null || sharedUuids == null || sharedUuids.isEmpty() || variant == null || variant.isEmpty() || want <= 0) return 0;
        
        long remaining = want;
        long got = 0;
        
        // 1) 先从旧版存储提取
        for (java.util.UUID uuid : sharedUuids) {
            if (remaining <= 0) break;
            var legacy = com.portable.storage.player.StoragePersistence.loadStorage(server, uuid);
            for (int i = 0; i < legacy.getCapacity() && remaining > 0; i++) {
                ItemStack disp = legacy.getDisplayStack(i);
                if (disp.isEmpty()) continue;
                if (net.minecraft.item.ItemStack.areItemsAndComponentsEqual(disp, variant)) {
                    long can = Math.min(remaining, legacy.getCountByIndex(i));
                    if (can > 0) {
                        long t = legacy.takeByIndex(i, can, System.currentTimeMillis());
                        got += t;
                        remaining -= t;
                    }
                }
            }
            // 保存旧版变更
            com.portable.storage.player.StoragePersistence.saveStorage(server, uuid, legacy);
        }
        
        // 2) 再从新版存储提取
        if (remaining > 0) {
            String key = ItemKeyHasher.hash(variant, null);
            if (key != null && !key.isEmpty()) {
                for (java.util.UUID uuid : sharedUuids) {
                    if (remaining <= 0) break;
                    long taken = PlayerStore.remove(server, uuid, key, remaining, System.currentTimeMillis());
                    if (taken > 0) {
                        got += taken;
                        remaining -= taken;
                    }
                }
                if (got > 0) {
                    TemplateIndex index = TemplateIndex.load(server);
                    index.incRef(key, -got);
                    if (index.find(key) != null && index.find(key).ref <= 0) {
                        TemplateSlices.removeTemplate(() -> server, index, key);
                    }
                    index.save(server);
                }
            }
        }
        
        return got;
    }
}


