package com.portable.storage.block;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.portable.storage.blockentity.BoundBarrelBlockEntity;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.storage.StorageType;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        // 绑定木桶不需要 tick 方法，使用即时处理
        return null;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
            // 检查绑定木桶所有者的仓库类型
            if (boundBarrel.getOwnerUuid() != null) {
                // 获取所有者玩家
                net.minecraft.server.network.ServerPlayerEntity ownerPlayer = world.getServer().getPlayerManager().getPlayer(boundBarrel.getOwnerUuid());
                if (ownerPlayer != null) {
                    PlayerStorageAccess access = (PlayerStorageAccess) ownerPlayer;
                    StorageType storageType = access.portableStorage$getStorageType();
                    
                    // 如果是初级仓库，禁用绑定木桶的交互
                    if (storageType == StorageType.PRIMARY) {
                        player.sendMessage(net.minecraft.text.Text.translatable("portable-storage.message.primary_storage_barrel_restricted"), true);
                        return ActionResult.CONSUME;
                    }
                }
            }
            
            // 检查是否是Shift+右键（打开筛选配置）
            if (player.isSneaking()) {
                // 发送打开筛选界面的网络包
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    (net.minecraft.server.network.ServerPlayerEntity) player,
                    new com.portable.storage.net.payload.OpenBarrelFilterS2CPayload(pos)
                );
            } else {
                // 普通右键打开木桶界面
                player.openHandledScreen(boundBarrel);
                // 切换为漏斗界面后，统计按漏斗检查计数
                player.incrementStat(Stats.INSPECT_HOPPER);
            }
        }
        
        return ActionResult.CONSUME;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BoundBarrelBlockEntity boundBarrel) {
                // 绑定木桶使用即时处理，不需要归还输出槽位物品
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

