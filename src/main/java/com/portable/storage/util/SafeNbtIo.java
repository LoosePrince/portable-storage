package com.portable.storage.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

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
    private static final ConcurrentHashMap<Path, ReentrantLock> FILE_LOCKS = new ConcurrentHashMap<>();
    
    private SafeNbtIo() {}
    
    /**
     * 安全读取压缩NBT文件，损坏时自动隔离
     */
    public static NbtCompound readCompressed(Path file, NbtSizeTracker tracker) throws IOException {
        Path normalized = normalize(file);
        ReentrantLock lock = lockFor(normalized);
        lock.lock();
        try {
            if (!Files.exists(normalized)) {
                return tryRestoreBackup(normalized, tracker);
            }
            
            // 检查是否已标记为损坏
            if (CORRUPT_FILES.contains(normalized)) {
                return tryRestoreBackup(normalized, tracker);
            }
            
            try {
                return NbtIo.readCompressed(normalized, tracker);
            } catch (IOException e) {
                NbtCompound restored = tryRestoreBackup(normalized, tracker);
                if (restored != null) {
                    return restored;
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
            Files.createDirectories(normalized.getParent());
            
            Path tempFile = normalized.resolveSibling(normalized.getFileName() + TEMP_SUFFIX);
            Path backupFile = normalized.resolveSibling(normalized.getFileName() + BACKUP_SUFFIX);
            
            try {
                // 如果目标文件存在，先创建备份
                if (Files.exists(normalized)) {
                    Files.copy(normalized, backupFile, StandardCopyOption.REPLACE_EXISTING);
                }
                
                // 写入到临时文件
                NbtIo.writeCompressed(nbt, tempFile);
                
                // 确保数据写入磁盘
                try (var channel = Files.newByteChannel(
                        tempFile,
                        EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.SYNC))) {
                    if (channel instanceof java.nio.channels.FileChannel fc) {
                        fc.force(true);
                    }
                } catch (UnsupportedOperationException | java.io.IOException ignored) {
                    // 某些文件系统不支持强制同步
                }
                
                // 原子替换
                try {
                    Files.move(tempFile, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException atomic) {
                    PortableStorage.LOGGER.warn("文件系统不支持原子移动，退回普通替换: {}", normalized);
                    Files.move(tempFile, normalized, StandardCopyOption.REPLACE_EXISTING);
                }
                
                // 验证写入的文件
                try {
                    NbtCompound verify = NbtIo.readCompressed(normalized, NbtSizeTracker.ofUnlimitedBytes());
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
    private static void isolateCorruptFile(Path file, IOException originalException) {
        Path normalized = normalize(file);
        try {
            Path corruptFile = normalized.resolveSibling(normalized.getFileName() + CORRUPT_SUFFIX);
            if (Files.exists(normalized)) {
                Files.move(normalized, corruptFile, StandardCopyOption.REPLACE_EXISTING);
            }
            CORRUPT_FILES.add(normalized);
            
            PortableStorage.LOGGER.error("检测到损坏的NBT文件，已隔离: {} -> {}", 
                normalized, corruptFile, originalException);
        } catch (IOException e) {
            PortableStorage.LOGGER.error("无法隔离损坏文件: {}", normalized, e);
        }
    }
    
    /**
     * 检查文件是否已标记为损坏
     */
    public static boolean isCorrupt(Path file) {
        return CORRUPT_FILES.contains(normalize(file));
    }
    
    /**
     * 清除损坏文件标记（用于修复后）
     */
    public static void clearCorruptMark(Path file) {
        CORRUPT_FILES.remove(normalize(file));
    }
    
    /**
     * 获取所有损坏文件列表
     */
    public static Set<Path> getCorruptFiles() {
        return Set.copyOf(CORRUPT_FILES);
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
                        NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
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
    
    private static ReentrantLock lockFor(Path file) {
        return FILE_LOCKS.computeIfAbsent(normalize(file), k -> new ReentrantLock());
    }
    
    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }
    
    private static NbtCompound tryRestoreBackup(Path file, NbtSizeTracker tracker) {
        Path backupFile = file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
        if (!Files.exists(backupFile)) {
            return null;
        }
        try {
            PortableStorage.LOGGER.warn("主文件缺失或损坏，尝试从备份恢复: {}", file);
            NbtCompound backup = NbtIo.readCompressed(backupFile, tracker);
            if (backup != null) {
                Files.move(backupFile, file, StandardCopyOption.REPLACE_EXISTING);
                CORRUPT_FILES.remove(file);
                PortableStorage.LOGGER.info("从备份成功恢复文件: {}", file);
                return backup;
            }
        } catch (IOException backupException) {
            PortableStorage.LOGGER.warn("备份文件也损坏: {}", backupFile, backupException);
        }
        return null;
    }
}
