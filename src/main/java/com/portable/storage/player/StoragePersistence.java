package com.portable.storage.player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.portable.storage.storage.StorageInventory;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

/**
 * 读取/写入离线玩家的随身仓库到玩家数据文件。
 */
public final class StoragePersistence {
    private StoragePersistence() {}

    public static StorageInventory loadStorage(MinecraftServer server, UUID uuid) {
        StorageInventory inv = new StorageInventory(0);
        try {
            Path dir = server.getSavePath(WorldSavePath.PLAYERDATA);
            Path file = dir.resolve(uuid.toString() + ".dat");
            if (!Files.exists(file)) return inv; // 空
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            if (root != null && root.contains("portable_storage")) {
                inv.readNbt(root.getCompound("portable_storage"));
            }
        } catch (IOException ignored) {}
        return inv;
    }

    public static void saveStorage(MinecraftServer server, UUID uuid, StorageInventory inv) {
        try {
            Path dir = server.getSavePath(WorldSavePath.PLAYERDATA);
            Files.createDirectories(dir);
            Path file = dir.resolve(uuid.toString() + ".dat");
            NbtCompound root;
            if (Files.exists(file)) {
                root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
                if (root == null) root = new NbtCompound();
            } else {
                root = new NbtCompound();
            }
            NbtCompound out = new NbtCompound();
            inv.writeNbt(out);
            root.put("portable_storage", out);
            NbtIo.writeCompressed(root, file);
        } catch (IOException ignored) {}
    }
}


