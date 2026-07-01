package com.portablestorage.event;

import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.world.SpaceRiftManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;

public class SpaceRiftEventHandler {
    public static void register() {
        // 拦截方块放置/使用
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (world.dimension().equals(SpaceRiftManager.DIMENSION_KEY)) {
                if (player.isCreative() || player.isSpectator()) return InteractionResult.PASS;
                
                PlayerWarehouse warehouse = WarehouseService.get(player);
                if (SpaceRiftManager.isOutsideBorder(null, warehouse, hitResult.getBlockPos().relative(hitResult.getDirection()))) {
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        // 拦截方块破坏
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (world.dimension().equals(SpaceRiftManager.DIMENSION_KEY)) {
                if (player.isCreative() || player.isSpectator()) return InteractionResult.PASS;

                PlayerWarehouse warehouse = WarehouseService.get(player);
                if (SpaceRiftManager.isOutsideBorder(null, warehouse, pos)) {
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });
    }
}

