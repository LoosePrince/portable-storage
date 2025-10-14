package com.portable.storage.block;

import com.mojang.serialization.MapCodec;
import com.portable.storage.blockentity.BoundBarrelBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 自定义的绑定木桶方块
 * 破坏后掉落普通木桶（带有绑定信息）
 */
public class BoundBarrelBlock extends BlockWithEntity {
    public static final MapCodec<BoundBarrelBlock> CODEC = createCodec(BoundBarrelBlock::new);
    
    public BoundBarrelBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BoundBarrelBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
            player.openHandledScreen(boundBarrel);
            // 切换为漏斗界面后，统计按漏斗检查计数
            player.incrementStat(Stats.INSPECT_HOPPER);
        }
        
        return ActionResult.CONSUME;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
                // 掉落木桶内部的标记物品
                ItemScatterer.spawn(world, pos, boundBarrel);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        BlockEntity blockEntity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
            // 检查是否是绑定者本人且按住 Shift
            PlayerEntity player = builder.getOptional(LootContextParameters.THIS_ENTITY) instanceof PlayerEntity p ? p : null;
            boolean isOwnerSneaking = false;
            
            if (player != null && boundBarrel.getOwnerUuid() != null) {
                isOwnerSneaking = player.getUuid().equals(boundBarrel.getOwnerUuid()) && player.isSneaking();
            }
            
            ItemStack barrel = new ItemStack(Items.BARREL);
            
            // 只有绑定者 Shift+左键才掉落绑定木桶，否则掉落普通木桶
            if (isOwnerSneaking) {
                boundBarrel.copyDataToItem(barrel);
            }
            
            return List.of(barrel);
        }
        return List.of(new ItemStack(Items.BARREL));
    }
}

