package com.portablestorage.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.storage.service.WarehouseService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;

public class SpaceRiftManager {
    public static final Identifier DIM_ID = PortableStorage.id("space_rift");
    public static final ResourceKey<Level> DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, DIM_ID);
    private static final int DEFAULT_PLOT_GRID_SIZE = 32;
    private static final Map<UUID, Set<ChunkPos>> FORCED_CHUNKS_BY_PLAYER = new HashMap<>();

    public static ServerLevel getWorld(MinecraftServer server) {
        return server.getLevel(DIMENSION_KEY);
    }

    public static void enterOrExitRift(ServerPlayer player, PlayerWarehouse warehouse) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null)
            return;

        WarehouseService.commitIfWarehouseChanged(player, warehouse, "space_rift.teleport", () -> {
            ServerLevel currentLevel = (ServerLevel) player.level();
            if (currentLevel.dimension().equals(DIMENSION_KEY)) {
                // 退出裂隙 (正常退出，保存位置)
                exitRift(player, warehouse, false);
            } else {
                // 进入裂隙
                enterRift(player, warehouse);
            }
            return null;
        });
    }

    private static void enterRift(ServerPlayer player, PlayerWarehouse warehouse) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        ServerLevel riftLevel = getWorld(server);
        if (riftLevel == null)
            return;

        // 保存返回点 (仅当当前不在裂隙维度时才保存)
        if (!player.level().dimension().equals(DIMENSION_KEY)) {
            warehouse.setRiftReturnDim(player.level().dimension().identifier());
            warehouse.setRiftReturnPos(player.blockPosition());
            warehouse.setRiftReturnYaw(player.getYRot());
            warehouse.setRiftReturnPitch(player.getXRot());
        }

        // 分配或获取地块
        if (!hasValidRiftPlot(warehouse)) {
            allocatePlot(server, player.getUUID(), warehouse);
        }

        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        ensurePlotInitialized(riftLevel, origin, warehouse);
        updatePlotForcedLoading(player, warehouse, true);

        // 传送
        BlockPos lastPos = warehouse.getRiftLastPos();
        if (lastPos != null && isOutsideBorder(null, warehouse, lastPos)) {
            lastPos = null;
            warehouse.setRiftLastPos(null);
        }
        if (lastPos != null) {
            player.teleportTo(riftLevel, lastPos.getX() + 0.5, lastPos.getY(), lastPos.getZ() + 0.5,
                    java.util.Set.of(), warehouse.getRiftLastYaw(), warehouse.getRiftLastPitch(), false);
        } else {
            BlockPos spawnPos = getPlotCenterBlock(origin).above();
            player.teleportTo(riftLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    java.util.Set.of(), 0, 0, false);
        }

        // 应用个人边界
        applyPersonalBorder(player, warehouse);
        warehouse.setRiftBorderResendTicks(20);

        // 移除复制体
        removeAvatar(player);
    }

    private static void exitRift(ServerPlayer player, PlayerWarehouse warehouse, boolean isVoidFall) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();

        if (isVoidFall) {
            // 掉入虚空，清除裂隙内的记录位置，下次进入回到默认点
            warehouse.setRiftLastPos(null);
            removeAvatar(player);
        } else {
            // 正常退出，保存裂隙内的位置
            warehouse.setRiftLastPos(player.blockPosition());
            warehouse.setRiftLastYaw(player.getYRot());
            warehouse.setRiftLastPitch(player.getXRot());

            // 创建复制体
            spawnAvatar(player, warehouse);
        }

        Identifier returnDimId = warehouse.getRiftReturnDim();
        BlockPos returnPos = warehouse.getRiftReturnPos();

        ServerLevel targetLevel = null;
        if (returnDimId != null) {
            targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, returnDimId));
        }

        if (targetLevel == null) {
            targetLevel = server.overworld();
        }

        if (returnPos == null) {
            // 1.21.11 中不再暴露 getSharedSpawnPos，改为使用重生数据的世界出生点
            var respawnData = targetLevel.getRespawnData();
            if (respawnData != null && respawnData.pos() != null) {
                returnPos = respawnData.pos();
            } else {
                returnPos = BlockPos.ZERO;
            }
        }

        // 传送回返回点，并重置速度（防止虚空掉落时的惯性）
        player.teleportTo(targetLevel, returnPos.getX() + 0.5, returnPos.getY(), returnPos.getZ() + 0.5,
                java.util.Set.of(), warehouse.getRiftReturnYaw(), warehouse.getRiftReturnPitch(), false);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        // 重置边界
        resetToWorldBorder(player);
    }

    public static void handleVoidFall(ServerPlayer player, PlayerWarehouse warehouse) {
        if (player.getY() < player.level().dimensionType().minY() - 1) {
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "space_rift.void_fall", () -> {
                exitRift(player, warehouse, true);
                return null;
            });
        }
    }

    public static void checkAndTeleportBack(ServerPlayer player, PlayerWarehouse warehouse) {
        if (player.isCreative() || player.isSpectator())
            return;

        // 关键：必须确保玩家还在裂隙维度，否则主世界的坐标会触发边界回传
        if (!player.level().dimension().equals(DIMENSION_KEY))
            return;

        // 如果玩家已经在虚空高度，交给 handleVoidFall 处理，不要回传以免坐标冲突
        if (player.getY() < player.level().dimensionType().minY())
            return;

        if (isOutsideBorder(null, warehouse, player.blockPosition())) {
            ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
            BlockPos center = getPlotCenterBlock(origin).above();
            ServerLevel riftLevel = (ServerLevel) player.level();
            player.teleportTo(riftLevel, center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                    java.util.Set.of(), player.getYRot(), player.getXRot(), false);
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("message.portablestorage.rift_outside_border"));
        }
    }

    public static void tickBorderResend(ServerPlayer player, PlayerWarehouse warehouse) {
        if (!player.level().dimension().equals(DIMENSION_KEY))
            return;
        if (warehouse.tickRiftBorderResend()) {
            applyPersonalBorder(player, warehouse);
        }
    }

    private static void allocatePlot(MinecraftServer server, UUID playerId, PlayerWarehouse warehouse) {
        int spacing = ModConfig.riftPlotSpacingChunks;
        Set<ChunkPos> occupiedPlots = getOccupiedPlots(server, playerId);
        int totalSlots = DEFAULT_PLOT_GRID_SIZE * DEFAULT_PLOT_GRID_SIZE;
        int startIndex = Math.floorMod(playerId.hashCode(), totalSlots);

        for (int offset = 0; offset < totalSlots; offset++) {
            int index = (startIndex + offset) % totalSlots;
            ChunkPos candidate = plotAtIndex(index, spacing);
            if (!occupiedPlots.contains(candidate)) {
                warehouse.setRiftPlotX(candidate.x());
                warehouse.setRiftPlotZ(candidate.z());
                return;
            }
        }

        int overflowIndex = totalSlots;
        while (true) {
            ChunkPos candidate = plotAtIndex(overflowIndex++, spacing);
            if (!occupiedPlots.contains(candidate)) {
                warehouse.setRiftPlotX(candidate.x());
                warehouse.setRiftPlotZ(candidate.z());
                return;
            }
        }
    }

    private static Set<ChunkPos> getOccupiedPlots(MinecraftServer server, UUID excludedPlayerId) {
        Set<ChunkPos> occupied = new HashSet<>();
        for (PlayerWarehouse other : WarehouseService.getAll(server)) {
            if (other.getOwnerUuid().equals(excludedPlayerId) || !hasValidRiftPlot(other)) {
                continue;
            }
            occupied.add(new ChunkPos(other.getRiftPlotX(), other.getRiftPlotZ()));
        }
        return occupied;
    }

    private static ChunkPos plotAtIndex(int index, int spacing) {
        int gridX = index % DEFAULT_PLOT_GRID_SIZE;
        int gridZ = index / DEFAULT_PLOT_GRID_SIZE;
        return new ChunkPos(gridX * spacing, gridZ * spacing);
    }

    private static boolean hasValidRiftPlot(PlayerWarehouse warehouse) {
        return warehouse.hasRiftPlot() && warehouse.getRiftPlotZ() != Integer.MIN_VALUE;
    }

    private static BlockPos getPlotCenterBlock(ChunkPos origin) {
        return new BlockPos(origin.getMiddleBlockX(), ModConfig.riftFloorY, origin.getMiddleBlockZ());
    }

    private static void ensurePlotInitialized(ServerLevel world, ChunkPos origin, PlayerWarehouse warehouse) {
        // 确保区块加载
        world.getChunk(origin.x(), origin.z());

        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();

        // 仅刷新默认位置那一个点的方块 (根据要求)
        BlockPos centerPos = getPlotCenterBlock(origin);
        world.setBlockAndUpdate(centerPos, stone);

        warehouse.setRiftInitialized(true);
    }

    public static void applyPersonalBorder(ServerPlayer player, PlayerWarehouse warehouse) {
        if (player.isCreative() || player.isSpectator() || !ModConfig.enableRiftBorder) {
            resetToWorldBorder(player);
            return;
        }

        if (!hasValidRiftPlot(warehouse))
            return;

        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        double borderSize = ModConfig.riftChunkSize * 16.0;
        double centerX = origin.getMiddleBlockX();
        double centerZ = origin.getMiddleBlockZ();

        WorldBorder border = new WorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setWarningBlocks(ModConfig.riftBorderWarningBlocks);
        border.setWarningTime(0);

        player.connection.send(new ClientboundInitializeBorderPacket(border));
    }

    public static void resetToWorldBorder(ServerPlayer player) {
        WorldBorder worldBorder = player.level().getWorldBorder();
        player.connection.send(new ClientboundInitializeBorderPacket(worldBorder));
    }

    public static boolean isOutsideBorder(ServerPlayer player, PlayerWarehouse warehouse, BlockPos pos) {
        if (!ModConfig.enableRiftBorder || !hasValidRiftPlot(warehouse))
            return false;

        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        int chunkSize = ModConfig.riftChunkSize;
        double borderSize = chunkSize * 16.0;
        double centerX = origin.getMiddleBlockX();
        double centerZ = origin.getMiddleBlockZ();
        int minX = (int) Math.floor(centerX - borderSize / 2.0);
        int minZ = (int) Math.floor(centerZ - borderSize / 2.0);
        int maxX = (int) Math.floor(centerX + borderSize / 2.0 - 1.0);
        int maxZ = (int) Math.floor(centerZ + borderSize / 2.0 - 1.0);

        return pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ;
    }

    public static void spawnAvatar(ServerPlayer player, PlayerWarehouse warehouse) {
        if (!ModConfig.enableRiftAvatar)
            return;

        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null)
            return;
        ServerLevel riftLevel = getWorld(server);
        if (riftLevel == null)
            return;

        removeAvatar(player);

        com.portablestorage.entity.RiftAvatarEntity avatar = new com.portablestorage.entity.RiftAvatarEntity(
                com.portablestorage.entity.ModEntities.RIFT_AVATAR, riftLevel);
        avatar.setOwnerId(player.getUUID());
        BlockPos pos = player.blockPosition();
        avatar.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        avatar.setYRot(player.getYRot());
        avatar.setXRot(player.getXRot());
        avatar.setCustomName(net.minecraft.network.chat.Component.translatable("entity.portablestorage.rift_avatar",
                player.getScoreboardName()));

        riftLevel.addFreshEntity(avatar);
        warehouse.setAvatarUuid(avatar.getUUID());
    }

    public static void removeAvatar(ServerPlayer player) {
        var warehouse = WarehouseService.get(player);
        UUID avatarUuid = warehouse.getAvatarUuid();
        warehouse.setAvatarUuid(null);

        if (avatarUuid == null)
            return;

        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null)
            return;
        ServerLevel riftLevel = getWorld(server);
        if (riftLevel == null)
            return;

        net.minecraft.world.entity.Entity e = riftLevel.getEntity(avatarUuid);
        if (e != null) {
            e.discard();
        }
    }

    public static void updatePlotForcedLoading(ServerPlayer player, PlayerWarehouse warehouse, boolean forced) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null)
            return;
        ServerLevel riftLevel = getWorld(server);
        if (riftLevel == null)
            return;

        if (!hasValidRiftPlot(warehouse)) {
            clearTrackedForcedChunks(player.getUUID(), riftLevel);
            return;
        }

        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        Set<ChunkPos> previous = FORCED_CHUNKS_BY_PLAYER.getOrDefault(player.getUUID(), Set.of());
        Set<ChunkPos> next = forced && ModConfig.enableRiftForcedLoading
                ? getForcedChunks(origin)
                : Set.of();

        if (previous.equals(next)) {
            return;
        }

        for (ChunkPos chunk : previous) {
            if (!next.contains(chunk)) {
                setChunkForced(riftLevel, chunk, false);
            }
        }
        for (ChunkPos chunk : next) {
            if (!previous.contains(chunk)) {
                setChunkForced(riftLevel, chunk, true);
            }
        }

        if (next.isEmpty()) {
            FORCED_CHUNKS_BY_PLAYER.remove(player.getUUID());
            cleanupLegacyForcedChunks(riftLevel, origin);
            reapplyTrackedForcedChunks(riftLevel);
        } else {
            FORCED_CHUNKS_BY_PLAYER.put(player.getUUID(), next);
        }
    }

    private static void clearTrackedForcedChunks(UUID playerId, ServerLevel riftLevel) {
        Set<ChunkPos> previous = FORCED_CHUNKS_BY_PLAYER.remove(playerId);
        if (previous == null) {
            return;
        }
        for (ChunkPos chunk : previous) {
            setChunkForced(riftLevel, chunk, false);
        }
        reapplyTrackedForcedChunks(riftLevel);
    }

    private static void reapplyTrackedForcedChunks(ServerLevel riftLevel) {
        for (Set<ChunkPos> chunks : FORCED_CHUNKS_BY_PLAYER.values()) {
            for (ChunkPos chunk : chunks) {
                setChunkForced(riftLevel, chunk, true);
            }
        }
    }

    private static Set<ChunkPos> getForcedChunks(ChunkPos origin) {
        int plotChunkRadius = (int) Math.ceil((ModConfig.riftChunkSize - 1) / 2.0);
        int range = Math.min(ModConfig.riftForcedLoadingRange, plotChunkRadius);
        Set<ChunkPos> chunks = new HashSet<>();
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                chunks.add(new ChunkPos(origin.x() + x, origin.z() + z));
            }
        }
        return chunks;
    }

    private static void cleanupLegacyForcedChunks(ServerLevel riftLevel, ChunkPos origin) {
        int plotChunkRadius = (int) Math.ceil((ModConfig.riftChunkSize - 1) / 2.0);
        int cleanupRange = Math.max(5, plotChunkRadius + 2);
        for (int x = -cleanupRange; x <= cleanupRange; x++) {
            for (int z = -cleanupRange; z <= cleanupRange; z++) {
                setChunkForced(riftLevel, new ChunkPos(origin.x() + x, origin.z() + z), false);
            }
        }
    }

    private static void setChunkForced(ServerLevel riftLevel, ChunkPos chunk, boolean forced) {
        try {
            riftLevel.setChunkForced(chunk.x(), chunk.z(), forced);
        } catch (Exception ignored) {
        }
    }
}
