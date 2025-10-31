package com.portable.storage.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.portable.storage.PortableStorage;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.entity.RiftAvatarEntity;

import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

/**
 * 空间裂隙：维度/分地/传送/边界 管理
 */
public final class SpaceRiftManager {
    public static final Identifier DIM_ID = Identifier.of("portable-storage", "space_rift");
    public static final RegistryKey<World> DIMENSION_KEY = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, DIM_ID);

    // 每位玩家的地块原点（区块坐标），以及返回点
    private static final Map<UUID, ChunkPos> playerPlotOrigin = new HashMap<>();
    private static final Map<UUID, GlobalPos> playerReturnPoint = new HashMap<>();
    private static final Map<UUID, WorldBorder> personalBorders = new HashMap<>();
    private static final Map<UUID, net.minecraft.entity.Entity> avatars = new HashMap<>();
    private static final Map<UUID, BlockPos> lastRiftPos = new HashMap<>();

    // 裂隙大小从配置中获取，默认为1区块
    private static final int PLOT_SPACING_CHUNKS = 64; // 相邻玩家相隔64区块
    private static final int PLOT_HEIGHT = 100; // 逻辑高度，仅用于边界判断
    private static final int FLOOR_Y = 64;
    private static final int BOTTOM_Y = 0;
    private static final int TOP_Y = FLOOR_Y + PLOT_HEIGHT; // 顶部极限层

    private SpaceRiftManager() {}

    public static ServerWorld getWorld(MinecraftServer server) {
        return server.getWorld(DIMENSION_KEY);
    }

    public static ChunkPos ensureAllocatedPlot(MinecraftServer server, UUID playerId) {
        return playerPlotOrigin.computeIfAbsent(playerId, id -> allocatePlotFor(id));
    }

    private static ChunkPos allocatePlotFor(UUID playerId) {
        // 使用UUID哈希分配到X正方向，避免碰撞（简单：按在线顺序增长亦可）
        int index = Math.floorMod(playerId.hashCode(), 1024); // 限制到安全范围，避免极端坐标
        int x = index * PLOT_SPACING_CHUNKS;
        int z = 0;
        return new ChunkPos(x, z);
    }

    public static BlockPos getPlotCenterBlock(ChunkPos origin) {
        // 计算裂隙区域的中心点，考虑配置的裂隙直径
        int chunkSize = getPlotChunkSize();
        // 对于直径，中心点偏移 = (直径 - 1) * 8，这样确保边界正确对齐
        int centerOffset = (chunkSize - 1) * 8; // 每个区块8个方块偏移
        int x = origin.getCenterX() + centerOffset;
        int z = origin.getCenterZ() + centerOffset;
        return new BlockPos(x, FLOOR_Y + 1, z);
    }

    public static void rememberReturnPoint(ServerPlayerEntity player) {
        playerReturnPoint.put(player.getUuid(), GlobalPos.create(player.getWorld().getRegistryKey(), player.getBlockPos()));
    }

    public static GlobalPos getReturnPoint(UUID playerId) {
        return playerReturnPoint.get(playerId);
    }

    public static void clearReturnPoint(UUID playerId) {
        playerReturnPoint.remove(playerId);
    }

    public static boolean isInsideOwnPlot(ServerPlayerEntity player, BlockPos pos) {
        ChunkPos origin = playerPlotOrigin.get(player.getUuid());
        if (origin == null) return false;
        int minX = origin.getStartX();
        int minZ = origin.getStartZ();
        int maxX = minX + 16 * getPlotChunkSize() - 1;
        int maxZ = minZ + 16 * getPlotChunkSize() - 1;
        
        boolean insideXZ = pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        
        // 只有在启用高度限制时才检查Y坐标
        if (ServerConfig.getInstance().isLimitRiftHeight()) {
            return insideXZ && pos.getY() >= 0 && pos.getY() < 164;
        } else {
            return insideXZ;
        }
    }

    public static void ensurePlotInitialized(ServerWorld world, ChunkPos origin) {
        // 确保区块已加载
        world.getChunk(origin.x, origin.z);

        // 生成完整 16x16 地板于 FLOOR_Y
        net.minecraft.block.BlockState stone = net.minecraft.block.Blocks.SMOOTH_STONE.getDefaultState();
        net.minecraft.block.BlockState barrier = net.minecraft.block.Blocks.BARRIER.getDefaultState();
        int minX = origin.getStartX();
        int minZ = origin.getStartZ();
        int maxX = minX + 16 * getPlotChunkSize() - 1;
        int maxZ = minZ + 16 * getPlotChunkSize() - 1;
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY() - 1;
        int bottomStart = Math.max(BOTTOM_Y, worldBottom);
        int bottomEnd = Math.min(bottomStart + 9, worldTop);
        int topEnd = Math.min(TOP_Y, worldTop);
        int topStart = Math.max(topEnd - 9, worldBottom);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(new BlockPos(x, FLOOR_Y, z), stone);
                
                // 只有在启用高度限制时才生成屏障
                if (ServerConfig.getInstance().isLimitRiftHeight()) {
                    for (int y = bottomStart; y <= bottomEnd; y++) {
                        world.setBlockState(new BlockPos(x, y, z), barrier);
                    }
                    for (int y = topStart; y <= topEnd; y++) {
                        world.setBlockState(new BlockPos(x, y, z), barrier);
                    }
                }
            }
        }
    }

    public static void applyPersonalBorder(ServerPlayerEntity player) {
        // 创造和观察者模式玩家移除边界限制
        if (player.isCreative() || player.isSpectator()) {
            resetToWorldBorder(player);
            return;
        }
        
        ChunkPos origin = playerPlotOrigin.get(player.getUuid());
        if (origin == null) return;
        BlockPos center = getPlotCenterBlock(origin);
        WorldBorder border = new WorldBorder();
        border.setCenter(center.getX(), center.getZ());
        // 使用配置中的裂隙大小，每个区块16x16方块
        double borderSize = getPlotChunkSize() * 16.0;
        border.setSize(borderSize);
        border.setWarningBlocks(0);
        border.setWarningTime(0);
        personalBorders.put(player.getUuid(), border);
        player.networkHandler.sendPacket(new WorldBorderInitializeS2CPacket(border));
    }

    public static void resetToWorldBorder(ServerPlayerEntity player) {
        personalBorders.remove(player.getUuid());
        WorldBorder worldBorder = player.getWorld().getWorldBorder();
        player.networkHandler.sendPacket(new WorldBorderInitializeS2CPacket(worldBorder));
    }

    public static void ensureAvatarOnExit(ServerPlayerEntity player) {
        // 在裂隙维度创建/更新复制体
        ServerWorld rift = getWorld(player.getServer());
        if (rift == null) return;
        
        // 检查玩家最后位置是否在裂隙虚空中
        BlockPos lastPos = lastRiftPos.get(player.getUuid());
        if (lastPos != null && isInRiftVoid(rift, lastPos)) {
            // 如果最后位置在虚空中，不生成复制体
            removeAvatar(player);
            PortableStorage.LOGGER.debug("Player {} last position was in rift void, no avatar created", player.getName().getString());
            return;
        }
        
        removeAvatar(player);
        // 选择放置位置：优先使用玩家在裂隙内的最后记录位置，否则使用自己地块中心
        ChunkPos origin = ensureAllocatedPlot(player.getServer(), player.getUuid());
        BlockPos center = getPlotCenterBlock(origin);
        BlockPos recorded = lastRiftPos.getOrDefault(player.getUuid(), center);
        BlockPos pos = clampToPlot(origin, recorded);
        try {
            net.minecraft.entity.Entity avatar = RiftAvatarEntity.spawn(rift, pos, player.getUuid());
            if (avatar != null) {
                avatar.setCustomName(net.minecraft.text.Text.translatable(PortableStorage.MOD_ID + ".rift_avatar", player.getName().getString()));
            }
            avatars.put(player.getUuid(), avatar);
            PortableStorage.LOGGER.debug("Rift avatar created for {} at {}", player.getName().getString(), pos);
        } catch (Throwable t) {
            PortableStorage.LOGGER.error("Failed to create rift avatar for {}", player.getName().getString(), t);
        }
    }

    public static void updateLastRiftPos(ServerPlayerEntity player) {
        lastRiftPos.put(player.getUuid(), player.getBlockPos());
    }

    /**
     * 检查位置是否在裂隙虚空中
     */
    public static boolean isInRiftVoid(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey() == DIMENSION_KEY && pos.getY() < world.getBottomY();
    }

    private static BlockPos clampToPlot(ChunkPos origin, BlockPos pos) {
        int minX = origin.getStartX();
        int minZ = origin.getStartZ();
        int maxX = minX + 16 * getPlotChunkSize() - 1;
        int maxZ = minZ + 16 * getPlotChunkSize() - 1;
        int x = Math.min(Math.max(pos.getX(), minX), maxX);
        int z = Math.min(Math.max(pos.getZ(), minZ), maxZ);
        int y = Math.max(pos.getY(), BOTTOM_Y);
        return new BlockPos(x, y, z);
    }

    public static void removeAvatar(ServerPlayerEntity player) {
        net.minecraft.entity.Entity e = avatars.remove(player.getUuid());
        if (e != null) e.discard();
        // 兜底：服务器重启等情况下，内存映射可能丢失，扫描裂隙维度移除残留复制体
        ServerWorld rift = getWorld(player.getServer());
        if (rift != null) {
            net.minecraft.util.math.Box whole = new net.minecraft.util.math.Box(
                -3.0e7, rift.getBottomY(), -3.0e7,
                 3.0e7, rift.getTopY(),  3.0e7
            );
            java.util.List<com.portable.storage.entity.RiftAvatarEntity> list = rift.getEntitiesByClass(
                com.portable.storage.entity.RiftAvatarEntity.class,
                whole,
                ent -> player.getUuid().equals(ent.getOwner())
            );
            for (var av : list) {
                av.discard();
            }
        }
    }

    /**
     * 获取玩家地块原点（用于边界保护）
     */
    public static ChunkPos getPlayerPlotOrigin(UUID playerId) {
        return playerPlotOrigin.get(playerId);
    }

    /**
     * 获取地块区块大小（用于边界保护）
     */
    public static int getPlotChunkSize() {
        return ServerConfig.getInstance().getRiftSize();
    }
    
    public static void safelyKickPlayerFromRift(ServerPlayerEntity player) {
        try {
            java.util.UUID id = player.getUuid();
            net.minecraft.util.math.GlobalPos returnPoint = getReturnPoint(id);
            
            // 先清理区块加载状态
            setPlayerPlotForced(player.getServer(), id, false);
            
            if (returnPoint != null) {
                net.minecraft.server.world.ServerWorld targetWorld = player.getServer().getWorld(returnPoint.getDimension());
                if (targetWorld != null) {
                    net.minecraft.util.math.BlockPos pos = returnPoint.getPos();
                    player.teleport(targetWorld, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
                    player.sendMessage(net.minecraft.text.Text.translatable(PortableStorage.MOD_ID + ".rift_feature_disabled_returned"), true);
                    clearReturnPoint(id);
                    removeAvatar(player);
                } else {
                    // 兜底：传送到主世界出生点
                    net.minecraft.server.world.ServerWorld overworld = player.getServer().getOverworld();
                    net.minecraft.util.math.BlockPos spawn = overworld.getSpawnPos();
                    player.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), player.getPitch());
                    player.sendMessage(net.minecraft.text.Text.translatable(PortableStorage.MOD_ID + ".rift_feature_disabled_returned"), true);
                    clearReturnPoint(id);
                    removeAvatar(player);
                }
            } else {
                // 没有返回点：传送到主世界出生点
                net.minecraft.server.world.ServerWorld overworld = player.getServer().getOverworld();
                net.minecraft.util.math.BlockPos spawn = overworld.getSpawnPos();
                player.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), player.getPitch());
                player.sendMessage(net.minecraft.text.Text.translatable(PortableStorage.MOD_ID + ".rift_feature_disabled_returned"), true);
                removeAvatar(player);
            }
        } catch (Throwable ignored) {}
    }

    public static BlockPos getAvatarPositionOrCenter(ServerPlayerEntity player, ChunkPos origin) {
        // 优先获取复制体位置
        net.minecraft.entity.Entity avatar = avatars.get(player.getUuid());
        if (avatar != null && !avatar.isRemoved()) {
            return avatar.getBlockPos();
        }
        // 否则返回地块中心
        return getPlotCenterBlock(origin);
    }

    public static void setPlayerPlotForced(net.minecraft.server.MinecraftServer server, java.util.UUID playerId, boolean forced) {
        ServerWorld rift = getWorld(server);
        if (rift == null) return;
        ChunkPos origin = ensureAllocatedPlot(server, playerId);
        try {
            int size = getPlotChunkSize();
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    rift.setChunkForced(origin.x + dx, origin.z + dz, forced);
                }
            }
        } catch (Throwable ignored) {}
    }
}


