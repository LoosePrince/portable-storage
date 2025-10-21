package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portable.storage.block.TempBedBlock;
import com.portable.storage.net.ServerNetworkingHandlers;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 处理临时床方块的破坏和清理
 */
@Mixin(BedBlock.class)
public class BedBlockMixin {

    @Inject(method = "onBreak", at = @At("HEAD"))
    private void portableStorage$onBedBreak(World world, BlockPos pos, BlockState state, PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
        // 检查是否是临时床
        if (state.getBlock() instanceof TempBedBlock || ServerNetworkingHandlers.isTempBed(pos)) {
            // 临时床被破坏，清理完整的床（头部和脚部）
            ServerNetworkingHandlers.cleanupCompleteTempBed(pos, world);
        }
    }
}
