package com.portablestorage.world;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;

import java.util.UUID;

public class SpaceRiftManager {
    public static final ResourceLocation DIM_ID = PortableStorage.id("space_rift");
    public static final ResourceKey<Level> DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, DIM_ID);

    private static final int PLOT_SPACING_CHUNKS = 64;
    private static final int FLOOR_Y = 64;

    public static ServerLevel getWorld(MinecraftServer server) {
        return server.getLevel(DIMENSION_KEY);
    }

    public static void enterOrExitRift(ServerPlayer player, PlayerWarehouse warehouse) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel currentLevel = player.serverLevel();
        if (currentLevel.dimension().equals(DIMENSION_KEY)) {
            // 退出裂隙 (正常退出，保存位置)
            exitRift(player, warehouse, false);
        } else {
            // 进入裂隙
            enterRift(player, warehouse);
        }
    }

    private static void enterRift(ServerPlayer player, PlayerWarehouse warehouse) {
        MinecraftServer server = player.getServer();
        ServerLevel riftLevel = getWorld(server);
        if (riftLevel == null) return;

        // 保存返回点 (仅当当前不在裂隙维度时才保存)
        if (!player.level().dimension().equals(DIMENSION_KEY)) {
            warehouse.setRiftReturnDim(player.level().dimension().location());
            warehouse.setRiftReturnPos(player.blockPosition());
            warehouse.setRiftReturnYaw(player.getYRot());
            warehouse.setRiftReturnPitch(player.getXRot());
        }

        // 分配或获取地块
        if (!warehouse.hasRiftPlot()) {
            allocatePlot(player.getUUID(), warehouse);
        }

        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        ensurePlotInitialized(riftLevel, origin, warehouse);

        // 传送
        BlockPos lastPos = warehouse.getRiftLastPos();
        if (lastPos != null) {
            player.teleportTo(riftLevel, lastPos.getX() + 0.5, lastPos.getY(), lastPos.getZ() + 0.5, warehouse.getRiftLastYaw(), warehouse.getRiftLastPitch());
        } else {
            BlockPos spawnPos = getPlotCenterBlock(origin).above();
            player.teleportTo(riftLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
        }
        
        // 应用个人边界
        applyPersonalBorder(player, warehouse);
    }

    private static void exitRift(ServerPlayer player, PlayerWarehouse warehouse, boolean isVoidFall) {
        MinecraftServer server = player.getServer();
        
        if (isVoidFall) {
            // 掉入虚空，清除裂隙内的记录位置，下次进入回到默认点
            warehouse.setRiftLastPos(null);
        } else {
            // 正常退出，保存裂隙内的位置
            warehouse.setRiftLastPos(player.blockPosition());
            warehouse.setRiftLastYaw(player.getYRot());
            warehouse.setRiftLastPitch(player.getXRot());
        }

        ResourceLocation returnDimId = warehouse.getRiftReturnDim();
        BlockPos returnPos = warehouse.getRiftReturnPos();

        ServerLevel targetLevel = null;
        if (returnDimId != null) {
            targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, returnDimId));
        }

        if (targetLevel == null) {
            targetLevel = server.overworld();
        }

        if (returnPos == null) {
            returnPos = targetLevel.getSharedSpawnPos();
        }

        // 传送回返回点，并重置速度（防止虚空掉落时的惯性）
        player.teleportTo(targetLevel, returnPos.getX() + 0.5, returnPos.getY(), returnPos.getZ() + 0.5, warehouse.getRiftReturnYaw(), warehouse.getRiftReturnPitch());
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        
        // 重置边界
        resetToWorldBorder(player);
    }

    public static void handleVoidFall(ServerPlayer player, PlayerWarehouse warehouse) {
        if (player.getY() < player.level().getMinBuildHeight() - 1) {
            exitRift(player, warehouse, true);
        }
    }

    public static void checkAndTeleportBack(ServerPlayer player, PlayerWarehouse warehouse) {
        if (player.isCreative() || player.isSpectator()) return;
        
        // 关键：必须确保玩家还在裂隙维度，否则主世界的坐标会触发边界回传
        if (!player.level().dimension().equals(DIMENSION_KEY)) return;
        
        // 如果玩家已经在虚空高度，交给 handleVoidFall 处理，不要回传以免坐标冲突
        if (player.getY() < player.level().getMinBuildHeight()) return;
        
        if (isOutsideBorder(null, warehouse, player.blockPosition())) {
            ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
            BlockPos center = getPlotCenterBlock(origin).above();
            player.teleportTo(player.serverLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.portablestorage.rift_outside_border"));
        }
    }

    private static void allocatePlot(UUID playerId, PlayerWarehouse warehouse) {
        int index = Math.floorMod(playerId.hashCode(), 1024);
        // 使用 32x32 的矩阵分布，充分利用 X 和 Z 轴
        int gridX = index % 32;
        int gridZ = index / 32;
        
        warehouse.setRiftPlotX(gridX * PLOT_SPACING_CHUNKS);
        warehouse.setRiftPlotZ(gridZ * PLOT_SPACING_CHUNKS);
    }

    private static BlockPos getPlotCenterBlock(ChunkPos origin) {
        int chunkSize = ModConfig.riftChunkSize;
        int centerOffset = (chunkSize - 1) * 8;
        int x = origin.getMiddleBlockX() + centerOffset;
        int z = origin.getMiddleBlockZ() + centerOffset;
        return new BlockPos(x, FLOOR_Y, z);
    }

    private static void ensurePlotInitialized(ServerLevel world, ChunkPos origin, PlayerWarehouse warehouse) {
        // 确保区块加载
        world.getChunk(origin.x, origin.z);

        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        
        // 仅刷新默认位置那一个点的方块 (根据要求)
        BlockPos centerPos = getPlotCenterBlock(origin);
        world.setBlockAndUpdate(centerPos, stone);
        
        warehouse.setRiftInitialized(true);
    }

    public static void applyPersonalBorder(ServerPlayer player, PlayerWarehouse warehouse) {
        if (player.isCreative() || player.isSpectator()) {
            resetToWorldBorder(player);
            return;
        }

        if (!warehouse.hasRiftPlot()) return;

        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        double borderSize = ModConfig.riftChunkSize * 16.0;
        // 计算精确的中心点，使其对齐方块边界。
        // 例如 size=16, minX=0, maxX=15，中心应为 8.0。
        double centerX = origin.getMinBlockX() + borderSize / 2.0;
        double centerZ = origin.getMinBlockZ() + borderSize / 2.0;
        
        WorldBorder border = new WorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setWarningBlocks(0);
        border.setWarningTime(0);
        
        player.connection.send(new ClientboundInitializeBorderPacket(border));
    }

    public static void resetToWorldBorder(ServerPlayer player) {
        WorldBorder worldBorder = player.level().getWorldBorder();
        player.connection.send(new ClientboundInitializeBorderPacket(worldBorder));
    }

    public static boolean isOutsideBorder(ServerPlayer player, PlayerWarehouse warehouse, BlockPos pos) {
        if (!warehouse.hasRiftPlot()) return false;
        
        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        int chunkSize = ModConfig.riftChunkSize;
        int minX = origin.getMinBlockX();
        int minZ = origin.getMinBlockZ();
        int maxX = minX + 16 * chunkSize - 1;
        int maxZ = minZ + 16 * chunkSize - 1;

        return pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ;
    }

    public static void updatePlotForcedLoading(ServerPlayer player, PlayerWarehouse warehouse, boolean forced) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel riftLevel = getWorld(server);
        if (riftLevel == null) return;

        if (!warehouse.hasRiftPlot()) return;

        ChunkPos origin = new ChunkPos(warehouse.getRiftPlotX(), warehouse.getRiftPlotZ());
        
        // 先清理可能存在的旧强制加载区块 (清理最大范围 5)
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                try {
                    riftLevel.setChunkForced(origin.x + x, origin.z + z, false);
                } catch (Exception ignored) {}
            }
        }

        if (forced && ModConfig.enableRiftForcedLoading) {
            int range = ModConfig.riftForcedLoadingRange;
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    try {
                        riftLevel.setChunkForced(origin.x + x, origin.z + z, true);
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}

