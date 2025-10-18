package com.portable.storage.event;

import com.portable.storage.world.SpaceRiftManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
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
    }

    private static boolean isAllowedBuild(ServerPlayerEntity player, BlockPos pos) {
        // 观察者模式和创造模式玩家可以自由建造和破坏
        if (player.isSpectator() || player.isCreative()) {
            return true;
        }
        
        // 普通玩家：XZ 必须在自己地块；Y 介于世界 bottom/top 层极限之间
        boolean insideXZ = SpaceRiftManager.isInsideOwnPlot(player, pos);
        int bottom = player.getWorld().getBottomY();
        int top = player.getWorld().getTopY() - 1;
        boolean insideY = pos.getY() >= bottom && pos.getY() <= top;
        return insideXZ && insideY;
    }
}


