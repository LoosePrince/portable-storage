package com.portable.storage.newstore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.portable.storage.PortableStorage;
import com.portable.storage.util.SafeNbtIo;

/**
 * 模板切片管理：slice_XXX.nbt 中 templates[key] = item_full
 * 单个切片超过阈值（50MB）时滚动到下一切片。
 */
public final class TemplateSlices {
    public static final long SLICE_MAX_BYTES = 50L * 1024L * 1024L; // 50MB
    private static final String KEY_TEMPLATES = "templates";
    private static final String KEY_VERSION = "ps_template_version";
    private static final String KEY_CHECKSUM = "ps_template_checksum";
    private static final String KEY_UPDATED_AT = "ps_template_updated_at";
    private static final int TEMPLATE_FILE_VERSION = 2;
    private static final int MAX_STRING_BYTES = 65535;

    private TemplateSlices() {}

    public static ItemStack getTemplate(MinecraftServerLike server, TemplateIndex index, String key, RegistryWrapper.WrapperLookup lookup) {
        TemplateIndex.Entry e = index.find(key);
        if (e == null) return ItemStack.EMPTY;
        Path sliceFile = StoragePaths.getSliceFile(server.getServer(), e.slice);
        if (!Files.exists(sliceFile)) return ItemStack.EMPTY;
        
        try {
            NbtCompound root = SafeNbtIo.readCompressed(sliceFile);
            root = validateOrRecoverSlice(sliceFile, root);
            if (root == null || !root.contains(KEY_TEMPLATES)) return ItemStack.EMPTY;
            NbtCompound templates = root.getCompound(KEY_TEMPLATES);
            if (!templates.contains(key)) return ItemStack.EMPTY;
            var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
            var parse = ItemStack.CODEC.parse(ops, templates.get(key));
            var result = parse.result();
            if (result.isPresent()) {
                return result.get();
            }
            return handleCorruptTemplateEntry(server, index, key, sliceFile, root, templates);
        } catch (IOException ex) {
            // SafeNbtIo已经处理了损坏文件隔离，这里只记录日志
            PortableStorage.LOGGER.warn("读取模板切片失败，文件可能已损坏: {}", sliceFile, ex);
        }
        return ItemStack.EMPTY;
    }

    public static int putTemplate(MinecraftServerLike server, TemplateIndex index, String key, ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        // 选择切片：尽量使用当前切片，必要时滚动
        int targetSlice = index.getOrAllocateSlice();
        if (wouldExceedLimit(server.getServer(), targetSlice, estimateSize(stack, lookup))) {
            index.rollToNextSlice();
            targetSlice = index.getOrAllocateSlice();
        }

        Path sliceFile = StoragePaths.getSliceFile(server.getServer(), targetSlice);
        StoragePaths.ensureDirectories(server.getServer());
        NbtCompound root;
        try {
            if (Files.exists(sliceFile)) {
                root = SafeNbtIo.readCompressed(sliceFile);
                root = validateOrRecoverSlice(sliceFile, root);
                if (root == null) root = new NbtCompound();
            } else {
                root = new NbtCompound();
            }
            NbtCompound templates = root.contains(KEY_TEMPLATES) ? root.getCompound(KEY_TEMPLATES) : new NbtCompound();
            var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
            var enc = ItemStack.CODEC.encodeStart(ops, stack);
            if (enc.result().isEmpty()) {
                PortableStorage.LOGGER.warn("无法序列化模板物品，已拒绝写入: {}", stack);
                return targetSlice;
            }
            NbtElement encoded = enc.result().get();
            if (!isTemplatePayloadSafe(encoded)) {
                PortableStorage.LOGGER.warn("检测到包含非法字符串或超长数据的物品模板，已拒绝写入: {}", stack);
                broadcastCorruptTemplate(server.getServer(), key);
                return targetSlice;
            }
            templates.put(key, encoded);
            stampTemplateMetadata(root, templates);
            SafeNbtIo.writeCompressed(root, sliceFile);

            int size = estimateSize(stack, lookup);
            index.put(key, targetSlice, size);
            StorageMemoryCache.markTemplateIndexDirty();
            return targetSlice;
        } catch (IOException ex) {
            PortableStorage.LOGGER.warn("写入模板切片失败: {}", sliceFile, ex);
        }
        return targetSlice;
    }

    public static void removeTemplate(MinecraftServerLike server, TemplateIndex index, String key) {
        TemplateIndex.Entry e = index.find(key);
        if (e == null) return;
        Path sliceFile = StoragePaths.getSliceFile(server.getServer(), e.slice);
        if (!Files.exists(sliceFile)) return;
        try {
            NbtCompound root = SafeNbtIo.readCompressed(sliceFile);
            root = validateOrRecoverSlice(sliceFile, root);
            if (root == null) return;
            if (root.contains(KEY_TEMPLATES)) {
                NbtCompound templates = root.getCompound(KEY_TEMPLATES);
                templates.remove(key);
                stampTemplateMetadata(root, templates);
            }
            SafeNbtIo.writeCompressed(root, sliceFile);
            index.remove(key);
            StorageMemoryCache.removeTemplateFromCache(key);
        } catch (IOException ignored) {}
    }

    private static boolean wouldExceedLimit(MinecraftServer server, int slice, int addBytes) {
        try {
            Path f = StoragePaths.getSliceFile(server, slice);
            long existing = Files.exists(f) ? Files.size(f) : 0L;
            return existing + Math.max(0, addBytes) > SLICE_MAX_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    private static int estimateSize(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        try {
            var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
            var enc = ItemStack.CODEC.encodeStart(ops, stack);
            Optional<net.minecraft.nbt.NbtElement> res = enc.result();
            if (res.isEmpty()) return 0;
            String s = res.get().toString();
            return s.getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 为了便于在非 MC 线程编写可测试代码，这里抽象出 server 访问接口。
     */
    public interface MinecraftServerLike {
        net.minecraft.server.MinecraftServer getServer();
    }
    
    private static NbtCompound validateOrRecoverSlice(Path file, NbtCompound root) {
        if (root == null) return null;
        if (isTemplateRootHealthy(root)) return root;
        PortableStorage.LOGGER.warn("模板切片元数据异常，尝试恢复: {}", file);
        if (SafeNbtIo.tryRestoreBackup(file)) {
            try {
                NbtCompound restored = SafeNbtIo.readCompressed(file);
                if (isTemplateRootHealthy(restored)) {
                    PortableStorage.LOGGER.info("模板切片已从备份恢复: {}", file);
                    return restored;
                }
            } catch (IOException ex) {
                PortableStorage.LOGGER.warn("备份恢复后读取失败: {}", file, ex);
            }
        }
        SafeNbtIo.isolateCorruptFile(file, new IOException("模板切片校验失败"));
        return null;
    }
    
    private static boolean isTemplateRootHealthy(NbtCompound root) {
        if (root == null || !root.contains(KEY_TEMPLATES)) return false;
        if (!root.contains(KEY_VERSION)) return false;
        if (root.getInt(KEY_VERSION) != TEMPLATE_FILE_VERSION) return false;
        long recorded = root.contains(KEY_CHECKSUM) ? root.getLong(KEY_CHECKSUM) : -1L;
        NbtCompound templates = root.getCompound(KEY_TEMPLATES);
        long expected = templates.isEmpty() ? 0L : computeChecksum(templates);
        return recorded == expected;
    }
    
    private static void stampTemplateMetadata(NbtCompound root, NbtCompound templates) {
        root.put(KEY_TEMPLATES, templates);
        root.putInt(KEY_VERSION, TEMPLATE_FILE_VERSION);
        root.putLong(KEY_CHECKSUM, templates.isEmpty() ? 0L : computeChecksum(templates));
        root.putLong(KEY_UPDATED_AT, System.currentTimeMillis());
    }
    
    private static long computeChecksum(NbtCompound templates) {
        CRC32 crc = new CRC32();
        List<String> keys = new ArrayList<>(templates.getKeys());
        keys.sort(String::compareTo);
        for (String templateKey : keys) {
            crc.update(templateKey.getBytes(StandardCharsets.UTF_8));
            NbtElement element = templates.get(templateKey);
            if (element != null) {
                crc.update(element.asString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return crc.getValue();
    }
    
    private static ItemStack handleCorruptTemplateEntry(MinecraftServerLike server, TemplateIndex index, String key, Path sliceFile, NbtCompound root, NbtCompound templates) {
        PortableStorage.LOGGER.warn("检测到损坏的模板条目，已移除: {} ({})", key, sliceFile.getFileName());
        templates.remove(key);
        stampTemplateMetadata(root, templates);
        try {
            SafeNbtIo.writeCompressed(root, sliceFile);
        } catch (IOException writeEx) {
            PortableStorage.LOGGER.error("写回移除损坏模板后的切片失败: {}", sliceFile, writeEx);
        }
        index.remove(key);
        StorageMemoryCache.removeTemplateFromCache(key);
        StorageMemoryCache.markTemplateIndexDirty();
        MinecraftServer mcServer = server.getServer();
        if (mcServer != null) {
            PlayerStore.purgeKeyFromAllPlayers(mcServer, key);
        }
        broadcastCorruptTemplate(mcServer, key);
        return ItemStack.EMPTY;
    }
    
    private static void broadcastCorruptTemplate(MinecraftServer server, String key) {
        if (server == null) return;
        Text msg = Text.translatable("portable-storage.message.corrupt_template_removed", key);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(msg, false);
        }
    }
    
    private static boolean isTemplatePayloadSafe(NbtElement element) {
        if (element == null) return false;
        ArrayDeque<NbtElement> deque = new ArrayDeque<>();
        deque.push(element);
        while (!deque.isEmpty()) {
            NbtElement current = deque.pop();
            if (current instanceof NbtString str) {
                if (!isValidString(str.asString())) {
                    return false;
                }
            } else if (current instanceof NbtCompound compound) {
                for (String childKey : compound.getKeys()) {
                    if (!isValidString(childKey)) {
                        return false;
                    }
                    deque.push(compound.get(childKey));
                }
            } else if (current instanceof NbtList list) {
                for (int i = 0; i < list.size(); i++) {
                    deque.push(list.get(i));
                }
            }
        }
        return true;
    }
    
    private static boolean isValidString(String value) {
        if (value == null) return false;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == 0) {
                return false;
            }
        }
        return true;
    }
}


