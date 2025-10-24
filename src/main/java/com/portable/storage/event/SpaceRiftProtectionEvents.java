package com.portable.storage.event;

import com.portable.storage.config.ServerConfig;
import com.portable.storage.world.SpaceRiftManager;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public final class SpaceRiftProtectionEvents {
    private SpaceRiftProtectionEvents() {}

    public static void register() {
        // 放置限制：仅允许在自己地块范围内、且在底/顶极限Y之间
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (world.getRegistryKey() != SpaceRiftManager.DIMENSION_KEY) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity)) return ActionResult.PASS;
            BlockPos placePos = hitResult.getBlockPos().offset(hitResult.getSide());
            if (!isAllowedBuild((ServerPlayerEntity) player, placePos)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // 破坏限制
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.getRegistryKey() != SpaceRiftManager.DIMENSION_KEY) return true;
            if (!(player instanceof ServerPlayerEntity)) return true;
            return isAllowedBuild((ServerPlayerEntity) player, pos);
        });

        // 定期检查并清理边界外的方块（每5秒检查一次）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 100 == 0) { // 每5秒（100 tick）
                cleanupBoundaryViolations(server);
            }
        });
    }

    private static boolean isAllowedBuild(ServerPlayerEntity player, BlockPos pos) {
        // 观察者模式和创造模式玩家可以自由建造和破坏
        if (player.isSpectator() || player.isCreative()) {
            return true;
        }
        
        // 普通玩家：XZ 必须在自己地块
        boolean insideXZ = SpaceRiftManager.isInsideOwnPlot(player, pos);
        
        // 检查高度限制配置
        boolean insideY = true;
        if (ServerConfig.getInstance().isLimitRiftHeight()) {
            // 启用高度限制时，使用原有的高度限制逻辑
            int bottom = player.getWorld().getBottomY();
            int top = player.getWorld().getTopY() - 1;
            insideY = pos.getY() >= bottom && pos.getY() <= top;
        }
        // 如果未启用高度限制，则不检查Y坐标限制
        
        return insideXZ && insideY;
    }

    /**
     * 清理边界外的方块
     */
    private static void cleanupBoundaryViolations(MinecraftServer server) {
        ServerWorld riftWorld = SpaceRiftManager.getWorld(server);
        if (riftWorld == null) return;

        // 获取所有在线玩家
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() != riftWorld) continue;
            if (player.isSpectator() || player.isCreative()) continue;

            // 检查玩家地块边界
            cleanupPlayerPlot(riftWorld, player);
        }
    }

    /**
     * 清理特定玩家地块的边界违规
     */
    private static void cleanupPlayerPlot(ServerWorld world, ServerPlayerEntity player) {
        // 获取玩家的地块边界
        var origin = SpaceRiftManager.getPlayerPlotOrigin(player.getUuid());
        if (origin == null) return;

        int chunkSize = SpaceRiftManager.getPlotChunkSize();
        int minX = origin.getStartX();
        int minZ = origin.getStartZ();
        int maxX = minX + 16 * chunkSize - 1;
        int maxZ = minZ + 16 * chunkSize - 1;

        // 检查边界外的方块并清理
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                // 只检查边界线
                boolean isBoundary = (x == minX - 1 || x == maxX + 1 || z == minZ - 1 || z == maxZ + 1);
                if (!isBoundary) continue;

                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    // 如果不是空气且不在允许范围内，则清理
                    if (!state.isAir() && !isAllowedBuild(player, pos)) {
                        world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }
}


