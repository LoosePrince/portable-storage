package com.portablestorage.block;

import com.portablestorage.block.entity.BoundBarrelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BoundBarrelBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public BoundBarrelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
                // 权限检查：只有所有者可以打开界面
                if (boundBarrel.getOwnerUuid() != null && !boundBarrel.getOwnerUuid().equals(player.getUUID())) {
                    player.displayClientMessage(Component.translatable("message.portablestorage.bound_barrel_no_permission")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                    return InteractionResult.FAIL;
                }
                player.openMenu(boundBarrel);
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
                if (tag.hasUUID("owner")) {
                    boundBarrel.setOwner(tag.getUUID("owner"), tag.getString("ownerName"));
                }
            }
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
            if (!level.isClientSide && !player.isCreative()) {
                boundBarrel.setHandledByPlayer(true); // 标记已由玩家处理掉落
                ItemStack drop;
                // 只有所有者在 Shift (蹲下) 时破坏才掉落绑定木桶，否则掉落普通木桶
                if (player.isSecondaryUseActive() && player.getUUID().equals(boundBarrel.getOwnerUuid())) {
                    drop = new ItemStack(com.portablestorage.item.ModItems.BOUND_BARREL);
                    // 保存所有者信息到物品
                    net.minecraft.nbt.CompoundTag tag = drop.getOrCreateTag();
                    tag.putUUID("owner", boundBarrel.getOwnerUuid());
                    tag.putString("ownerName", boundBarrel.getOwnerName());
                } else {
                    drop = new ItemStack(net.minecraft.world.item.Items.BARREL);
                }
                
                popResource(level, pos, drop);
                net.minecraft.world.Containers.dropContents(level, pos, boundBarrel.getInventory());
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
                // 如果不是由玩家破坏（例如爆炸、活塞），则掉落普通木桶和内部物品
                if (!boundBarrel.isHandledByPlayer()) {
                    if (!level.isClientSide) {
                        popResource(level, pos, new ItemStack(net.minecraft.world.item.Items.BARREL));
                        net.minecraft.world.Containers.dropContents(level, pos, boundBarrel.getInventory());
                    }
                }
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoundBarrelBlockEntity(pos, state);
    }
}

