package com.portablestorage.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

public class BoundBarrelBlock extends BaseEntityBlock {
    public static final MapCodec<BoundBarrelBlock> CODEC = simpleCodec(BoundBarrelBlock::new);
    public static final Property<Direction> FACING = BlockStateProperties.FACING;
    public static final Property<Boolean> OPEN = BlockStateProperties.OPEN;

    public BoundBarrelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
                // 权限检查：只有所有者可以打开界面
                if (boundBarrel.getOwnerUuid() != null && !boundBarrel.getOwnerUuid().equals(player.getUUID())) {
                    player.displayClientMessage(
                            Component.translatable("message.portablestorage.bound_barrel_no_permission")
                                    .withStyle(net.minecraft.ChatFormatting.RED),
                            true);
                    return InteractionResult.FAIL;
                }
                player.openMenu(boundBarrel);
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
                net.minecraft.nbt.CompoundTag tag = stack
                        .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                        .copyTag();
                if (tag.contains("owner")) {
                    java.util.Optional<String> ownerStrOpt = tag.getString("owner");
                    java.util.Optional<String> ownerNameOpt = tag.getString("ownerName");
                    if (ownerStrOpt.isPresent()) {
                        try {
                            java.util.UUID ownerUuid = java.util.UUID.fromString(ownerStrOpt.get());
                            String ownerName = ownerNameOpt.orElse("");
                            boundBarrel.setOwner(ownerUuid, ownerName);
                        } catch (IllegalArgumentException ignored) {
                            // ignore invalid UUID format
                        }
                    }
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
            if (!level.isClientSide() && !player.isCreative()) {
                boundBarrel.setHandledByPlayer(true); // 标记已由玩家处理掉落
                ItemStack drop;
                // 只有所有者在 Shift (蹲下) 时破坏才掉落绑定木桶，否则掉落普通木桶
                if (player.isSecondaryUseActive() && player.getUUID().equals(boundBarrel.getOwnerUuid())) {
                    drop = new ItemStack(com.portablestorage.item.ModItems.BOUND_BARREL);
                    // 保存所有者信息到物品
                    net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    tag.putString("owner", boundBarrel.getOwnerUuid().toString());
                    tag.putString("ownerName", boundBarrel.getOwnerName());
                    drop.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.of(tag));
                } else {
                    drop = new ItemStack(net.minecraft.world.item.Items.BARREL);
                }

                popResource(level, pos, drop);
                net.minecraft.world.Containers.dropContents(level, pos, boundBarrel.getInventory());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public void onBroken(net.minecraft.world.level.LevelAccessor world, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel && !boundBarrel.isHandledByPlayer()) {
            if (world instanceof Level level && !level.isClientSide()) {
                popResource(level, pos, new ItemStack(net.minecraft.world.item.Items.BARREL));
                net.minecraft.world.Containers.dropContents(level, pos, boundBarrel.getInventory());
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoundBarrelBlockEntity(pos, state);
    }
}
