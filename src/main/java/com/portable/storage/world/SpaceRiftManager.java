package com.portable.storage.world;

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
import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.BeaconBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    private static final int PLOT_CHUNK_SIZE = 1; // 16x16 一块
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
        int x = origin.getCenterX();
        int z = origin.getCenterZ();
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
        int maxX = minX + 16 * PLOT_CHUNK_SIZE - 1;
        int maxZ = minZ + 16 * PLOT_CHUNK_SIZE - 1;
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ && pos.getY() >= 0 && pos.getY() < 164;
    }

    public static void ensurePlotInitialized(ServerWorld world, ChunkPos origin) {
        // 确保区块已加载
        world.getChunk(origin.x, origin.z);

        // 生成完整 16x16 地板于 FLOOR_Y
        net.minecraft.block.BlockState stone = net.minecraft.block.Blocks.SMOOTH_STONE.getDefaultState();
        net.minecraft.block.BlockState barrier = net.minecraft.block.Blocks.BARRIER.getDefaultState();
        int minX = origin.getStartX();
        int minZ = origin.getStartZ();
        int maxX = minX + 16 * PLOT_CHUNK_SIZE - 1;
        int maxZ = minZ + 16 * PLOT_CHUNK_SIZE - 1;
        int worldBottom = world.getBottomY();
        int worldTop = world.getTopY() - 1;
        int bottomStart = Math.max(BOTTOM_Y, worldBottom);
        int bottomEnd = Math.min(bottomStart + 9, worldTop);
        int topEnd = Math.min(TOP_Y, worldTop);
        int topStart = Math.max(topEnd - 9, worldBottom);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(new BlockPos(x, FLOOR_Y, z), stone);
                for (int y = bottomStart; y <= bottomEnd; y++) {
                    world.setBlockState(new BlockPos(x, y, z), barrier);
                }
                for (int y = topStart; y <= topEnd; y++) {
                    world.setBlockState(new BlockPos(x, y, z), barrier);
                }
            }
        }
    }

    public static void applyPersonalBorder(ServerPlayerEntity player) {
        ChunkPos origin = playerPlotOrigin.get(player.getUuid());
        if (origin == null) return;
        BlockPos center = getPlotCenterBlock(origin);
        WorldBorder border = new WorldBorder();
        border.setCenter(center.getX(), center.getZ());
        border.setSize(16.0); // 直径16，刚好一块
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
        removeAvatar(player);
        // 选择放置位置：优先使用玩家在裂隙内的最后记录位置，否则使用自己地块中心
        ChunkPos origin = ensureAllocatedPlot(player.getServer(), player.getUuid());
        BlockPos center = getPlotCenterBlock(origin);
        BlockPos recorded = lastRiftPos.getOrDefault(player.getUuid(), center);
        BlockPos pos = clampToPlot(origin, recorded);
        try {
            net.minecraft.entity.Entity avatar = com.portable.storage.entity.RiftAvatarEntity.spawn(rift, pos, player.getUuid());
            if (avatar != null) {
                avatar.setCustomName(net.minecraft.text.Text.translatable("portable_storage.rift_avatar", player.getName().getString()));
            }
            avatars.put(player.getUuid(), avatar);
            com.portable.storage.PortableStorage.LOGGER.debug("Rift avatar created for {} at {}", player.getName().getString(), pos);
        } catch (Throwable t) {
            com.portable.storage.PortableStorage.LOGGER.error("Failed to create rift avatar for {}", player.getName().getString(), t);
        }
    }

    public static void updateLastRiftPos(ServerPlayerEntity player) {
        lastRiftPos.put(player.getUuid(), player.getBlockPos());
    }

    private static BlockPos clampToPlot(ChunkPos origin, BlockPos pos) {
        int minX = origin.getStartX();
        int minZ = origin.getStartZ();
        int maxX = minX + 16 * PLOT_CHUNK_SIZE - 1;
        int maxZ = minZ + 16 * PLOT_CHUNK_SIZE - 1;
        int x = Math.min(Math.max(pos.getX(), minX), maxX);
        int z = Math.min(Math.max(pos.getZ(), minZ), maxZ);
        int y = Math.max(pos.getY(), BOTTOM_Y);
        return new BlockPos(x, y, z);
    }

    public static void removeAvatar(ServerPlayerEntity player) {
        net.minecraft.entity.Entity e = avatars.remove(player.getUuid());
        if (e != null) e.discard();
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
            rift.setChunkForced(origin.x, origin.z, forced);
        } catch (Throwable ignored) {}
    }
}


