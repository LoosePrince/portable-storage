package com.portable.storage.block;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 临时床方块，破坏时不掉落物品，睡觉时不保存重生点
 */
public class TempBedBlock extends BedBlock {
    
    public TempBedBlock(DyeColor color, Settings settings) {
        super(color, settings);
    }
    
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // 不调用父类的onBreak方法，这样就不会掉落物品
        // 只移除方块状态并返回null
        world.removeBlock(pos, false);
        return null;
    }
}
