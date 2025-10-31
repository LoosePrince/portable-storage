package com.portable.storage.newstore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;

import com.portable.storage.util.SafeNbtIo;

/**
 * 模板切片管理：slice_XXX.nbt 中 templates[key] = item_full
 * 单个切片超过阈值（50MB）时滚动到下一切片。
 */
public final class TemplateSlices {
    public static final long SLICE_MAX_BYTES = 50L * 1024L * 1024L; // 50MB

    private TemplateSlices() {}

    public static ItemStack getTemplate(MinecraftServerLike server, TemplateIndex index, String key, RegistryWrapper.WrapperLookup lookup) {
        TemplateIndex.Entry e = index.find(key);
        if (e == null) return ItemStack.EMPTY;
        Path sliceFile = StoragePaths.getSliceFile(server.getServer(), e.slice);
        if (!Files.exists(sliceFile)) return ItemStack.EMPTY;
        
        try {
        NbtCompound root = SafeNbtIo.readCompressed(sliceFile);
            if (root == null || !root.contains("templates")) return ItemStack.EMPTY;
            NbtCompound templates = root.getCompound("templates");
            if (!templates.contains(key)) return ItemStack.EMPTY;
            var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
            var parse = ItemStack.CODEC.parse(ops, templates.get(key));
            return parse.result().orElse(ItemStack.EMPTY);
        } catch (IOException ex) {
            // SafeNbtIo已经处理了损坏文件隔离，这里只记录日志
            com.portable.storage.PortableStorage.LOGGER.warn("读取模板切片失败，文件可能已损坏: {}", sliceFile, ex);
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
                if (root == null) root = new NbtCompound();
            } else {
                root = new NbtCompound();
            }
            NbtCompound templates = root.contains("templates") ? root.getCompound("templates") : new NbtCompound();
            var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
            var enc = ItemStack.CODEC.encodeStart(ops, stack);
            enc.result().ifPresent(nbt -> templates.put(key, nbt));
            root.put("templates", templates);
            SafeNbtIo.writeCompressed(root, sliceFile);

            int size = estimateSize(stack, lookup);
            index.put(key, targetSlice, size);
            return targetSlice;
        } catch (IOException ignored) {}
        return targetSlice;
    }

    public static void removeTemplate(MinecraftServerLike server, TemplateIndex index, String key) {
        TemplateIndex.Entry e = index.find(key);
        if (e == null) return;
        Path sliceFile = StoragePaths.getSliceFile(server.getServer(), e.slice);
        if (!Files.exists(sliceFile)) return;
        try {
        NbtCompound root = SafeNbtIo.readCompressed(sliceFile);
            if (root == null) return;
            if (root.contains("templates")) {
                NbtCompound templates = root.getCompound("templates");
                templates.remove(key);
                root.put("templates", templates);
            }
            SafeNbtIo.writeCompressed(root, sliceFile);
            index.remove(key);
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
            return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
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
}


