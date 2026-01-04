package com.portablestorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 临时床方块，用于床升级的原地睡觉。
 * 睡觉时不保存重生点，破坏时不掉落物品。
 */
public class TempBedBlock extends BedBlock {
    public TempBedBlock(DyeColor color, Properties properties) {
        super(color, properties);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        level.removeBlock(pos, false);
        return state;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null; // 不需要方块实体
    }
}

