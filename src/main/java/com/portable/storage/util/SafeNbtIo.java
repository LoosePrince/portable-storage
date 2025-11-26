package com.portable.storage.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
// NbtSizeTracker 不存在于 1.20.1 API，改为使用无追踪的读取

import com.portable.storage.PortableStorage;

/**
 * 安全的NBT IO工具，支持原子写入、损坏检测和隔离
 */
public final class SafeNbtIo {
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak";
    private static final String CORRUPT_SUFFIX = ".corrupt";
    
    // 记录已隔离的损坏文件，避免重复处理
    private static final Set<Path> CORRUPT_FILES = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<Path, ReentrantLock> FILE_LOCKS = new ConcurrentHashMap<>();
    
    private SafeNbtIo() {}
    
    /**
     * 安全读取压缩NBT文件，损坏时自动隔离
     */
    public static NbtCompound readCompressed(Path file) throws IOException {
        Path normalized = normalize(file);
        ReentrantLock lock = lockFor(normalized);
        lock.lock();
        try {
            if (!Files.exists(normalized)) {
                return null;
            }
            
            // 检查是否已标记为损坏
            if (CORRUPT_FILES.contains(normalized)) {
                return null;
            }
            
            try {
                return NbtIo.readCompressed(normalized.toFile());
            } catch (IOException e) {
                // 尝试从备份恢复
                if (tryRestoreBackupInternal(normalized)) {
                    return NbtIo.readCompressed(normalized.toFile());
                }
                
                // 隔离损坏文件
                isolateCorruptFile(normalized, e);
                return null;
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 安全写入压缩NBT文件，使用原子写入和备份
     */
    public static void writeCompressed(NbtCompound nbt, Path file) throws IOException {
        if (nbt == null) {
            throw new IllegalArgumentException("NBT compound cannot be null");
        }
        
        Path normalized = normalize(file);
        ReentrantLock lock = lockFor(normalized);
        lock.lock();
        try {
            // 确保父目录存在
            Path parent = normalized.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            Path tempFile = normalized.resolveSibling(normalized.getFileName() + TEMP_SUFFIX);
            Path backupFile = getBackupFile(normalized);
            
            try {
                // 如果目标文件存在，先创建备份
                if (Files.exists(normalized)) {
                    Files.copy(normalized, backupFile, StandardCopyOption.REPLACE_EXISTING);
                }
                
                // 写入到临时文件
                NbtIo.writeCompressed(nbt, tempFile.toFile());
                
                // 确保数据写入磁盘
                try {
                    // 尝试强制同步到磁盘
                    try (var channel = Files.newByteChannel(tempFile, java.util.Set.of(java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.SYNC))) {
                        if (channel instanceof java.nio.channels.FileChannel fc) {
                            fc.force(true);
                        }
                    }
                } catch (UnsupportedOperationException | java.io.IOException ignored) {
                    // 某些文件系统不支持强制同步
                }
                
                // 原子替换
                Files.move(tempFile, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                
                // 验证写入的文件
                try {
                    NbtCompound verify = NbtIo.readCompressed(normalized.toFile());
                    if (verify == null || !verify.equals(nbt)) {
                        throw new IOException("写入验证失败");
                    }
                } catch (IOException verifyException) {
                    // 验证失败，尝试恢复备份
                    if (Files.exists(backupFile)) {
                        Files.move(backupFile, normalized, StandardCopyOption.REPLACE_EXISTING);
                        throw new IOException("写入验证失败，已恢复备份", verifyException);
                    }
                    throw verifyException;
                }
                
                // 写入成功，清理备份（可选）
                try {
                    Files.deleteIfExists(backupFile);
                } catch (IOException ignored) {
                    // 备份清理失败不影响主流程
                }
                
            } catch (IOException e) {
                // 清理临时文件
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
                
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 隔离损坏文件
     */
    public static void isolateCorruptFile(Path file, IOException originalException) {
        try {
            Path corruptFile = file.resolveSibling(file.getFileName() + CORRUPT_SUFFIX);
            Files.move(file, corruptFile, StandardCopyOption.REPLACE_EXISTING);
            CORRUPT_FILES.add(file);
            
            PortableStorage.LOGGER.error("检测到损坏的NBT文件，已隔离: {} -> {}", 
                file, corruptFile, originalException);
        } catch (IOException e) {
            PortableStorage.LOGGER.error("无法隔离损坏文件: {}", file, e);
        }
    }
    
    /**
     * 检查文件是否已标记为损坏
     */
    public static boolean isCorrupt(Path file) {
        return CORRUPT_FILES.contains(file);
    }
    
    /**
     * 清除损坏文件标记（用于修复后）
     */
    public static void clearCorruptMark(Path file) {
        CORRUPT_FILES.remove(file);
    }
    
    /**
     * 获取所有损坏文件列表
     */
    public static Set<Path> getCorruptFiles() {
        return Set.copyOf(CORRUPT_FILES);
    }
    
    /**
     * 尝试从备份恢复（即使原文件仍可读取，用于额外校验失败的场景）
     */
    public static boolean tryRestoreBackup(Path file) {
        Path normalized = normalize(file);
        ReentrantLock lock = lockFor(normalized);
        lock.lock();
        try {
            return tryRestoreBackupInternal(normalized);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 扫描目录中的损坏文件
     */
    public static int scanForCorruptFiles(Path directory) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return 0;
        }
        
        int corruptCount = 0;
        try (var stream = Files.list(directory)) {
            for (Path file : stream.toList()) {
                if (file.getFileName().toString().endsWith(".nbt") && !isCorrupt(file)) {
                    try {
                        NbtIo.readCompressed(file.toFile());
                    } catch (IOException e) {
                        isolateCorruptFile(file, e);
                        corruptCount++;
                    }
                }
            }
        } catch (IOException e) {
            PortableStorage.LOGGER.error("扫描损坏文件时出错: {}", directory, e);
        }
        
        return corruptCount;
    }
    
    private static Path getBackupFile(Path file) {
        return file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
    }
    
    private static boolean tryRestoreBackupInternal(Path file) {
        Path backupFile = getBackupFile(file);
        if (Files.exists(backupFile)) {
            try {
                PortableStorage.LOGGER.warn("主文件损坏，尝试从备份恢复: {}", file);
                NbtCompound backup = NbtIo.readCompressed(backupFile.toFile());
                if (backup != null) {
                    Files.move(backupFile, file, StandardCopyOption.REPLACE_EXISTING);
                    CORRUPT_FILES.remove(file);
                    PortableStorage.LOGGER.info("从备份成功恢复文件: {}", file);
                    return true;
                }
            } catch (IOException backupException) {
                PortableStorage.LOGGER.warn("备份文件也损坏: {}", backupFile, backupException);
            }
        }
        return false;
    }
    
    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }
    
    private static ReentrantLock lockFor(Path file) {
        return FILE_LOCKS.computeIfAbsent(file, p -> new ReentrantLock());
    }
}
