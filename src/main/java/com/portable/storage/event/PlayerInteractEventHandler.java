package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.util.StorageActivationConfirmation;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 处理玩家右键使用物品启用随身仓库的事件
 */
public class PlayerInteractEventHandler {
    
    public static void register() {
        UseItemCallback.EVENT.register(PlayerInteractEventHandler::onUseItem);
    }
    
    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        if (world.isClient) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }
        
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        ItemStack stack = player.getStackInHand(hand);
        
        // 检查配置
        ServerConfig config = ServerConfig.getInstance();
        if (!config.isRequireConditionToEnable()) {
            // 不需要条件启用，直接通过
            return TypedActionResult.pass(stack);
        }
        
        // 检查玩家是否已经启用
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        if (access.portableStorage$isStorageEnabled()) {
            // 已经启用，直接通过
            return TypedActionResult.pass(stack);
        }
        
        // 检查手持物品是否为启用道具
        String enableItemId = config.getEnableItem();
        String currentItemId = Registries.ITEM.getId(stack.getItem()).toString();
        
        if (!enableItemId.equals(currentItemId)) {
            // 不是启用道具，直接通过
            return TypedActionResult.pass(stack);
        }
        
        // 检查是否有待确认的激活请求
        if (StorageActivationConfirmation.hasPendingConfirmation(serverPlayer)) {
            // 确认激活
            if (StorageActivationConfirmation.confirmActivation(serverPlayer)) {
                return performStorageActivation(serverPlayer, stack, hand, config);
            } else {
                // 确认超时，取消激活
                StorageActivationConfirmation.cancelPendingConfirmation(serverPlayer);
                player.sendMessage(Text.translatable("portable_storage.message.activation_timeout")
                        .formatted(net.minecraft.util.Formatting.RED), false);
                return TypedActionResult.fail(stack);
            }
        }
        
        // 检查是否需要清空仓库数据，如果需要且仓库有数据，则要求确认
        if (config.isClearStorageOnEnable() && !access.portableStorage$getInventory().isEmpty()) {
            // 设置待确认状态
            StorageActivationConfirmation.setPendingConfirmation(serverPlayer);
            
            // 发送确认消息
            player.sendMessage(Text.translatable("portable_storage.message.confirm_activation")
                    .formatted(net.minecraft.util.Formatting.YELLOW), false);
            player.sendMessage(Text.translatable("portable_storage.message.confirm_activation_hint")
                    .formatted(net.minecraft.util.Formatting.GRAY), false);
            
            return TypedActionResult.fail(stack);
        }
        
        // 直接激活（仓库为空或不需要清空数据）
        return performStorageActivation(serverPlayer, stack, hand, config);
    }
    
    /**
     * 执行仓库激活流程
     */
    private static TypedActionResult<ItemStack> performStorageActivation(ServerPlayerEntity player, ItemStack stack, Hand hand, ServerConfig config) {
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        
        // 启用玩家随身仓库
        access.portableStorage$setStorageEnabled(true);
        
        // 检查是否需要清空仓库数据
        if (config.isClearStorageOnEnable()) {
            access.portableStorage$getInventory().clear();
            PortableStorage.LOGGER.info("Cleared storage data for player {} when enabling storage", 
                player.getName().getString());
        }
        
        // 检查是否需要消耗道具
        if (config.isConsumeEnableItem()) {
            if (stack.getCount() > 1) {
                stack.decrement(1);
            } else {
                player.setStackInHand(hand, ItemStack.EMPTY);
            }
        }
        
        // 发送成功消息
        String itemName = stack.getItem().getName().getString();
        player.sendMessage(Text.translatable("portable_storage.message.storage_enabled", itemName)
                .formatted(net.minecraft.util.Formatting.GREEN), false);
        
        PortableStorage.LOGGER.info("Player {} enabled portable storage with item {}", 
            player.getName().getString(), config.getEnableItem());
        
        return TypedActionResult.success(player.getStackInHand(hand));
    }
}
