package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.player.PlayerStorageAccess;
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
        
        // 启用玩家随身仓库
        access.portableStorage$setStorageEnabled(true);
        
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
        player.sendMessage(Text.translatable("portable_storage.message.storage_enabled", itemName), false);
        
        PortableStorage.LOGGER.info("Player {} enabled portable storage with item {}", 
            player.getName().getString(), enableItemId);
        
        return TypedActionResult.success(player.getStackInHand(hand));
    }
}
