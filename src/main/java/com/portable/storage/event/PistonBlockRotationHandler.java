package com.portable.storage.event;

import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.player.PlayerStorageService;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 活塞升级方块朝向处理器
 * 当活塞升级激活时，使用 Shift+左键（主手持活塞）可以改变有朝向的方块的朝向
 */
public class PistonBlockRotationHandler {
    
    public static void register() {
        AttackBlockCallback.EVENT.register(PistonBlockRotationHandler::onAttackBlock);
    }
    
    /**
     * 处理方块攻击事件
     */
    private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        // 只处理服务器端
        if (world.isClient) {
            return ActionResult.PASS;
        }
        
        // 只处理主手
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        
        // 检查是否是服务器玩家
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        
        // 检查是否按住了 Shift 键
        if (!serverPlayer.isSneaking()) {
            return ActionResult.PASS;
        }
        
        // 检查主手是否手持活塞
        ItemStack mainHandStack = serverPlayer.getMainHandStack();
        if (mainHandStack.isEmpty() || !mainHandStack.isOf(net.minecraft.item.Items.PISTON)) {
            return ActionResult.PASS;
        }
        
        // 检查活塞升级是否激活
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(serverPlayer);
        if (!upgrades.isPistonUpgradeActive()) {
            return ActionResult.PASS;
        }
        
        // 获取点击的方块位置和状态
        BlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();
        String blockName = block.getName().getString();
        
        // 尝试旋转方块
        BlockState newState = rotateBlockState(blockState, direction);
        if (newState != null && !newState.equals(blockState)) {
            // 设置新的方块状态
            world.setBlockState(pos, newState);
            
            // 发送消息给玩家
            serverPlayer.sendMessage(Text.translatable("portable_storage.piston.block_rotated", blockName), true);
            
            return ActionResult.SUCCESS;
        }
        
        return ActionResult.PASS;
    }
    
    /**
     * 尝试旋转方块状态
     */
    private static BlockState rotateBlockState(BlockState state, Direction clickedFace) {
        
        // 处理漏斗的特殊属性
        if (state.contains(Properties.HOPPER_FACING)) {
            Direction currentFacing = state.get(Properties.HOPPER_FACING);
            Direction newFacing = getNextDirection(currentFacing, clickedFace);
            return state.with(Properties.HOPPER_FACING, newFacing);
        }
        
        // 处理方向性方块（如活塞、投掷器等）
        if (state.contains(Properties.FACING)) {
            Direction currentFacing = state.get(Properties.FACING);
            Direction newFacing = getNextDirection(currentFacing, clickedFace);
            return state.with(Properties.FACING, newFacing);
        }
        
        // 处理水平朝向方块（如箱子、床等）
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            Direction currentFacing = state.get(Properties.HORIZONTAL_FACING);
            Direction newFacing = getNextHorizontalDirection(currentFacing);
            return state.with(Properties.HORIZONTAL_FACING, newFacing);
        }
        
        // 处理轴方块（如原木、石英柱等）
        if (state.contains(Properties.AXIS)) {
            var currentAxis = state.get(Properties.AXIS);
            var newAxis = getNextAxis(currentAxis);
            return state.with(Properties.AXIS, newAxis);
        }
        
        // 处理楼梯
        if (state.contains(Properties.HORIZONTAL_FACING) && state.contains(Properties.BLOCK_HALF)) {
            Direction currentFacing = state.get(Properties.HORIZONTAL_FACING);
            Direction newFacing = getNextHorizontalDirection(currentFacing);
            return state.with(Properties.HORIZONTAL_FACING, newFacing);
        }
        
        // 处理半砖
        if (state.contains(Properties.SLAB_TYPE)) {
            SlabType currentType = state.get(Properties.SLAB_TYPE);
            SlabType newType = getNextSlabType(currentType);
            return state.with(Properties.SLAB_TYPE, newType);
        }
        
        return null; // 无法旋转
    }
    
    /**
     * 获取下一个方向（全方向）
     */
    private static Direction getNextDirection(Direction current, Direction clickedFace) {
        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            if (directions[i] == current) {
                return directions[(i + 1) % directions.length];
            }
        }
        return current;
    }
    
    /**
     * 获取下一个水平方向
     */
    private static Direction getNextHorizontalDirection(Direction current) {
        Direction[] horizontalDirections = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < horizontalDirections.length; i++) {
            if (horizontalDirections[i] == current) {
                return horizontalDirections[(i + 1) % horizontalDirections.length];
            }
        }
        return current;
    }
    
    /**
     * 获取下一个轴
     */
    private static net.minecraft.util.math.Direction.Axis getNextAxis(net.minecraft.util.math.Direction.Axis current) {
        net.minecraft.util.math.Direction.Axis[] axes = {
            net.minecraft.util.math.Direction.Axis.X,
            net.minecraft.util.math.Direction.Axis.Y,
            net.minecraft.util.math.Direction.Axis.Z
        };
        for (int i = 0; i < axes.length; i++) {
            if (axes[i] == current) {
                return axes[(i + 1) % axes.length];
            }
        }
        return current;
    }
    
    /**
     * 获取下一个半砖类型（排除双层）
     */
    private static SlabType getNextSlabType(SlabType current) {
        // 只处理底部和顶部，排除双层
        SlabType[] types = {SlabType.BOTTOM, SlabType.TOP};
        for (int i = 0; i < types.length; i++) {
            if (types[i] == current) {
                return types[(i + 1) % types.length];
            }
        }
        // 如果当前是双层，不处理
        if (current == SlabType.DOUBLE) {
            return current;
        }
        return current;
    }
}
