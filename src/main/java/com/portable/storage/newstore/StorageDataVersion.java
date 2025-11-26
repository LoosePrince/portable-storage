package com.portable.storage.newstore;

import net.minecraft.nbt.NbtCompound;

/**
 * 新版存储数据的版本标记工具。
 * 目前仅用于 templates/ 与 players/ 下的 NBT 文件，以便在结构变化时做兼容处理。
 */
public final class StorageDataVersion {
    public static final String KEY = "portable_storage_data_version";
    public static final int CURRENT_VERSION = 2;

    private StorageDataVersion() {}

    public static void stamp(NbtCompound root) {
        if (root != null) {
            root.putInt(KEY, CURRENT_VERSION);
        }
    }

    public static int read(NbtCompound root) {
        if (root == null || !root.contains(KEY)) {
            return 0;
        }
        return root.getInt(KEY);
    }

    public static boolean isCompatible(NbtCompound root) {
        return read(root) <= CURRENT_VERSION;
    }
}

