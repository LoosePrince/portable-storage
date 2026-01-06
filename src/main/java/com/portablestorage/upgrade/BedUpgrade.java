package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.block.ModBlocks;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BedUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("bed");
    
    // 存储玩家和他们放置的临时床位置
    public static final Map<UUID, BlockPos> PLAYER_TEMP_BEDS = new HashMap<>();
    // 存储临时床位置和原始方块状态
    public static final Map<BlockPos, BlockState> TEMP_BED_ORIGINAL_STATES = new HashMap<>();

    public BedUpgrade() {
        super(
            ID,
            null,
            stack -> stack.is(ItemTags.BEDS)
        );
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.RED_BED);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.bed.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.bed.interaction_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 原地睡觉逻辑
            BlockPos pos = serverPlayer.blockPosition();
            Direction facing = serverPlayer.getDirection();
            BlockPos headPos = pos;
            BlockPos footPos = pos.relative(facing.getOpposite());

            // 检查空间是否可用
            if (!serverPlayer.level().getBlockState(headPos).canBeReplaced() || !serverPlayer.level().getBlockState(footPos).canBeReplaced()) {
                serverPlayer.displayClientMessage(Component.translatable("upgrade.portablestorage.bed.no_space"), true);
                return;
            }

            // 保存原始状态
            TEMP_BED_ORIGINAL_STATES.put(headPos, serverPlayer.level().getBlockState(headPos));
            TEMP_BED_ORIGINAL_STATES.put(footPos, serverPlayer.level().getBlockState(footPos));
            PLAYER_TEMP_BEDS.put(serverPlayer.getUUID(), headPos);

            // 放置临时床
            BlockState headState = ModBlocks.TEMP_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.HEAD);
            BlockState footState = ModBlocks.TEMP_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);

            serverPlayer.level().setBlock(headPos, headState, 3);
            serverPlayer.level().setBlock(footPos, footState, 3);

            // 尝试睡觉
            serverPlayer.startSleepInBed(headPos).ifLeft(problem -> {
                if (problem != null) {
                    serverPlayer.displayClientMessage(Component.translatable("block.minecraft.bed." + problem.name().toLowerCase()), true);
                    cleanupTempBed(serverPlayer);
                }
            });
            
            if (serverPlayer.isSleeping()) {
                serverPlayer.closeContainer();
            }
        }
    }

    public static void cleanupTempBed(ServerPlayer player) {
        BlockPos headPos = PLAYER_TEMP_BEDS.remove(player.getUUID());
        if (headPos != null) {
            BlockState headState = player.level().getBlockState(headPos);
            if (headState.is(ModBlocks.TEMP_BED)) {
                Direction facing = headState.getValue(BedBlock.FACING);
                BlockPos footPos = headPos.relative(facing.getOpposite());
                
                // 恢复原始状态
                BlockState originalHead = TEMP_BED_ORIGINAL_STATES.remove(headPos);
                if (originalHead != null) player.level().setBlock(headPos, originalHead, 3);
                
                BlockState originalFoot = TEMP_BED_ORIGINAL_STATES.remove(footPos);
                if (originalFoot != null) player.level().setBlock(footPos, originalFoot, 3);
            }
        }
    }
}

