package com.portable.storage.newstore;

import com.portable.storage.PortableStorage;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.player.StoragePersistence;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.StorageType;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import java.io.ByteArrayOutputStream;

/**
 * 新版存储的公共服务：对外部自动化与交互提供统一的“存入新版存储”入口。
 */
public final class NewStoreService {
    private NewStoreService() {}

    public static void insertForOnlinePlayer(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        // 检查单个物品大小限制
        ServerConfig config = ServerConfig.getInstance();
        if (config.isEnableSizeLimit()) {
            long itemSize = calculateItemSize(stack, player.getRegistryManager());
            long maxSize = config.getMaxStorageSizeBytes();
            
            if (itemSize > maxSize) {
                // 发送大小限制消息给玩家
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.size_limit_exceeded", 
                    formatSize(itemSize), formatSize(maxSize)), false);
                return;
            }
        }
        
        TemplateIndex index = StorageMemoryCache.getTemplateIndex();
        String key = ItemKeyHasher.hash(stack, player.getRegistryManager());
        if (key == null || key.isEmpty()) return;
        
        // 检查初级仓库容量限制（在创建新模板之前检查）
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        StorageType storageType = access.portableStorage$getStorageType();
        if (storageType.hasCapacityLimit()) {
            // 检查是否已达到容量限制
            if (!canAddNewItemType(player, stack)) {
                return;
            }
        }
        
        // 如果模板不存在，在内存中创建
        if (index.find(key) == null) {
            // 分配切片ID
            int slice = index.getOrAllocateSlice();
            
            // 创建新模板条目
            index.put(key, slice, 0);
            
            // 将模板添加到内存缓存
            StorageMemoryCache.addTemplate(key, stack.copy());
        } else if (!StorageMemoryCache.getTemplateCache().containsKey(key)) {
            // 模板索引已存在但内存缓存缺失时，补写缓存，避免界面不可见
            StorageMemoryCache.addTemplate(key, stack.copy());
            StorageMemoryCache.markTemplateIndexDirty();
        }
        
        // 纯内存操作：更新玩家数据和引用计数
        long now = System.currentTimeMillis();
        PlayerStore.add(server, player.getUuid(), key, stack.getCount(), now);
        index.incRef(key, stack.getCount());
        
        // 标记为脏，由定时任务处理文件IO
        StorageMemoryCache.markTemplateIndexDirty();
        ServerNetworkingHandlers.sendSync(player);
    }

    public static void insertForOfflineUuid(MinecraftServer server, java.util.UUID uuid, ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (server == null || uuid == null || stack == null || stack.isEmpty()) return;
        
        // 纯内存操作：不进行文件IO
        TemplateIndex index = StorageMemoryCache.getTemplateIndex();
        String key = ItemKeyHasher.hash(stack, lookup);
        if (key == null || key.isEmpty()) return;
        
        // 如果模板不存在，在内存中创建
        if (index.find(key) == null) {
            // 分配切片ID
            int slice = index.getOrAllocateSlice();
            
            // 创建新模板条目
            index.put(key, slice, 0);
            
            // 将模板添加到内存缓存
            StorageMemoryCache.addTemplate(key, stack.copy());
        } else if (!StorageMemoryCache.getTemplateCache().containsKey(key)) {
            // 模板索引已存在但内存缓存缺失时，补写缓存
            StorageMemoryCache.addTemplate(key, stack.copy());
            StorageMemoryCache.markTemplateIndexDirty();
        }
        
        // 更新玩家数据和引用计数
        long now = System.currentTimeMillis();
        PlayerStore.add(server, uuid, key, stack.getCount(), now);
        index.incRef(key, stack.getCount());
        
        // 标记为脏，由定时任务处理文件IO
        StorageMemoryCache.markTemplateIndexDirty();
    }

    public static long takeForOnlinePlayer(ServerPlayerEntity player, ItemStack variant, int want) {
        if (player == null || variant == null || variant.isEmpty() || want <= 0) return 0;
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        String key = ItemKeyHasher.hash(variant, player.getRegistryManager());
        if (key == null || key.isEmpty()) return 0;
        
        // 减少玩家物品数量
        long taken = PlayerStore.remove(server, player.getUuid(), key, want, System.currentTimeMillis());
        if (taken > 0) {
            // 更新引用计数
            TemplateIndex index = StorageMemoryCache.getTemplateIndex();
            index.incRef(key, -taken);
            
            // 如果引用计数为0，标记为待删除（不立即删除文件）
            if (index.find(key) != null && index.find(key).ref <= 0) {
                // 从内存缓存中移除模板
                StorageMemoryCache.getTemplateCache().remove(key);
                // 标记模板索引为脏，由定时任务处理文件删除
            }
            
            // 标记为脏，由定时任务处理文件IO
            StorageMemoryCache.markTemplateIndexDirty();
            ServerNetworkingHandlers.sendSync(player);
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
        
        // 减少玩家物品数量
        long taken = PlayerStore.remove(server, uuid, key, want, System.currentTimeMillis());
        if (taken > 0) {
            // 更新引用计数
            TemplateIndex index = StorageMemoryCache.getTemplateIndex();
            index.incRef(key, -taken);
            
            // 如果引用计数为0，标记为待删除（不立即删除文件）
            if (index.find(key) != null && index.find(key).ref <= 0) {
                // 从内存缓存中移除模板
                StorageMemoryCache.getTemplateCache().remove(key);
                // 标记模板索引为脏，由定时任务处理文件删除
            }
            
            // 标记为脏，由定时任务处理文件IO
            StorageMemoryCache.markTemplateIndexDirty();
            ItemStack result = variant.copy();
            result.setCount((int) Math.min(variant.getMaxCount(), taken));
            return result;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 清空玩家的新版存储数据
     */
    public static void clearPlayerStorage(ServerPlayerEntity player) {
        if (player == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        UUID uuid = player.getUuid();
        
        // 获取玩家当前的所有存储条目
        Map<String, PlayerStore.Entry> entries = PlayerStore.readAll(server, uuid);
        
        // 更新引用计数（减少所有物品的引用）
        TemplateIndex index = StorageMemoryCache.getTemplateIndex();
        for (PlayerStore.Entry entry : entries.values()) {
            if (entry.key != null && !entry.key.isEmpty() && entry.count > 0) {
                index.incRef(entry.key, -entry.count);
            }
        }
        
        // 清空玩家的存储条目
        PlayerStore.writeAll(server, uuid, new LinkedHashMap<>(), System.currentTimeMillis());
        
        // 标记为脏，由定时任务处理文件IO
        StorageMemoryCache.markTemplateIndexDirty();
        
        PortableStorage.LOGGER.info("Cleared new storage data for player {}", player.getName().getString());
    }

    /**
     * 构建共享视图：合并所有相关玩家的旧版+新版存储
     */
    public static StorageInventory buildSharedView(MinecraftServer server, java.util.UUID viewerUuid, java.util.Set<java.util.UUID> sharedUuids) {
        StorageInventory agg = new StorageInventory(0);
        if (server == null || sharedUuids == null || sharedUuids.isEmpty()) return agg;
        
        // 1) 先合并旧版存储
        for (java.util.UUID uuid : sharedUuids) {
            var legacy = StoragePersistence.loadStorage(server, uuid);
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
        // 使用内存缓存中的模板索引
        TemplateIndex index = StorageMemoryCache.getTemplateIndex();
        // 使用服务器的注册表上下文，确保附魔等基于注册表的数据正确解析
        var lookup = server.getRegistryManager();
        for (java.util.UUID uuid : sharedUuids) {
            var entries = PlayerStore.readAll(server, uuid);
            for (PlayerStore.Entry e : entries.values()) {
                if (e.count <= 0) continue;
                // 优先从内存缓存获取模板
                ItemStack stack = StorageMemoryCache.getTemplateCache().get(e.key);
                if (stack == null || stack.isEmpty()) {
                    // 如果内存缓存中没有，则从文件加载
                    stack = TemplateSlices.getTemplate(() -> server, index, e.key, lookup);
                    if (stack != null && !stack.isEmpty()) {
                        // 将加载的模板添加到内存缓存
                        StorageMemoryCache.addTemplate(e.key, stack);
                    }
                }
                if (stack == null || stack.isEmpty()) continue;
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
            var legacy = StoragePersistence.loadStorage(server, uuid);
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
            StoragePersistence.saveStorage(server, uuid, legacy);
        }
        
        // 2) 再从新版存储提取
        if (remaining > 0) {
            // 使用服务器的注册表上下文，确保与存储时使用相同的上下文
            var lookup = server.getRegistryManager();
            String key = ItemKeyHasher.hash(variant, lookup);
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
                    // 使用内存缓存中的模板索引
                    TemplateIndex index = StorageMemoryCache.getTemplateIndex();
                    index.incRef(key, -got);
                    if (index.find(key) != null && index.find(key).ref <= 0) {
                        TemplateSlices.removeTemplate(() -> server, index, key);
                        // 从内存缓存中移除模板
                        StorageMemoryCache.getTemplateCache().remove(key);
                    }
                    // 标记模板索引为脏，由定时任务处理
                    StorageMemoryCache.markTemplateIndexDirty();
                }
            }
        }
        
        return got;
    }
    
    
    /**
     * 计算单个物品的大小
     */
    private static long calculateItemSize(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (stack == null || stack.isEmpty()) return 0;
        
        try {
            // 将物品序列化为NBT
            NbtCompound nbt = new NbtCompound();
            var encoded = ItemStack.CODEC.encodeStart(
                net.minecraft.registry.RegistryOps.of(net.minecraft.nbt.NbtOps.INSTANCE, lookup), 
                stack
            );
            
            if (encoded.result().isEmpty()) return 0;
            
            net.minecraft.nbt.NbtElement itemNbt = encoded.result().get();
            nbt.put("item", itemNbt);
            nbt.putInt("count", stack.getCount());
            
            // 计算NBT大小
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, baos);
            return baos.size();
        } catch (Exception e) {
            // 估算大小
            return 64 + (stack.getComponents().isEmpty() ? 0 : 100);
        }
    }
    
    /**
     * 格式化字节大小
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * 快速检查仓库中是否有指定物品（用于漏斗性能优化）
     */
    public static boolean hasItemInStorage(MinecraftServer server, UUID ownerUuid, ItemStack itemStack) {
        if (itemStack.isEmpty()) return false;
        
        // 生成物品键
        String key = ItemKeyHasher.hash(itemStack, server.getRegistryManager());
        if (key == null) return false;
        
        // 检查玩家数据中是否有该物品
        Map<String, PlayerStore.Entry> entries = PlayerStore.readAll(server, ownerUuid);
        PlayerStore.Entry entry = entries.get(key);
        
        return entry != null && entry.count > 0;
    }
    
    /**
     * 检查是否可以添加新的物品种类到仓库中
     * @param player 玩家
     * @param stack 要添加的物品
     * @return true表示可以添加，false表示已达到容量限制
     */
    public static boolean canAddNewItemType(ServerPlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        
        // 获取玩家当前存储的物品种类数量
        var entries = PlayerStore.readAll(server, player.getUuid());
        int currentTypeCount = entries.size();
        
        // 检查要添加的物品是否已经存在
        String key = ItemKeyHasher.hash(stack, player.getRegistryManager());
        if (key != null && !key.isEmpty() && entries.containsKey(key)) {
            // 物品已存在，可以添加（只是增加数量）
            return true;
        }
        
        // 物品不存在，检查是否已达到容量限制
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        StorageType storageType = access.portableStorage$getStorageType();
        return currentTypeCount < storageType.getCapacityLimit();
    }

    /**
     * 检查玩家的新版存储是否为空
     * @param player 玩家
     * @return true表示存储为空，false表示有物品
     */
    public static boolean isPlayerStorageEmpty(ServerPlayerEntity player) {
        if (player == null) return true;
        
        MinecraftServer server = player.getServer();
        if (server == null) return true;
        
        // 获取玩家当前存储的所有条目
        Map<String, PlayerStore.Entry> entries = PlayerStore.readAll(server, player.getUuid());
        
        // 检查是否有任何物品数量大于0
        for (PlayerStore.Entry entry : entries.values()) {
            if (entry != null && entry.count > 0) {
                return false; // 有物品，存储不为空
            }
        }
        
        return true; // 没有物品或所有物品数量为0，存储为空
    }
}


