package com.portable.storage.newstore;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 新版分离/切片存储的路径常量与工具。
 */
public final class StoragePaths {
    public static final String ROOT_DIR_NAME = "portable_storage";
    public static final String TEMPLATES_DIR_NAME = "templates";
    public static final String PLAYERS_DIR_NAME = "players";
    public static final String INDEX_FILE_NAME = "index.nbt";

    private StoragePaths() {}

    public static Path getRootDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(ROOT_DIR_NAME);
    }

    public static Path getTemplatesDir(MinecraftServer server) {
        return getRootDir(server).resolve(TEMPLATES_DIR_NAME);
    }

    public static Path getPlayersDir(MinecraftServer server) {
        return getRootDir(server).resolve(PLAYERS_DIR_NAME);
    }

    public static Path getIndexFile(MinecraftServer server) {
        return getTemplatesDir(server).resolve(INDEX_FILE_NAME);
    }

    public static Path getSliceFile(MinecraftServer server, int sliceOrdinal) {
        String fileName = String.format("slice_%03d.nbt", Math.max(1, sliceOrdinal));
        return getTemplatesDir(server).resolve(fileName);
    }

    public static Path getPlayerFile(MinecraftServer server, UUID uuid) {
        return getPlayersDir(server).resolve(uuid.toString() + ".nbt");
    }

    public static void ensureDirectories(MinecraftServer server) {
        try {
            Files.createDirectories(getTemplatesDir(server));
        } catch (Exception ignored) {}
        try {
            Files.createDirectories(getPlayersDir(server));
        } catch (Exception ignored) {}
    }
}


