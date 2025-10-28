package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.StorageType;
import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.util.StorageActivationConfirmation;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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
            // 已经启用，检查是否要升级仓库类型
            StorageType currentType = access.portableStorage$getStorageType();
            if (currentType == StorageType.FULL) {
                // 已经是完整仓库，无需再次激活
                return TypedActionResult.pass(stack);
            }
            // 如果是初级仓库，允许升级到完整仓库
        }
        
        // 检查手持物品是否为启用道具
        String enableItemId = config.getEnableItem();
        String primaryStorageItemId = config.getPrimaryStorageItem();
        String currentItemId = Registries.ITEM.getId(stack.getItem()).toString();
        
        StorageType targetStorageType = null;
        if (enableItemId.equals(currentItemId)) {
            targetStorageType = StorageType.FULL;
        } else if (config.isEnablePrimaryStorage() && primaryStorageItemId.equals(currentItemId)) {
            targetStorageType = StorageType.PRIMARY;
        }
        
        if (targetStorageType == null) {
            // 不是启用道具，直接通过
            return TypedActionResult.pass(stack);
        }
        
        // 如果已经启用仓库，检查升级条件
        if (access.portableStorage$isStorageEnabled()) {
            StorageType currentType = access.portableStorage$getStorageType();
            if (currentType == StorageType.PRIMARY && targetStorageType == StorageType.FULL) {
                // 从初级仓库升级到完整仓库，允许
            } else if (currentType == StorageType.FULL && targetStorageType == StorageType.PRIMARY) {
                // 从完整仓库降级到初级仓库，不允许
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.cannot_downgrade_storage")
                        .formatted(net.minecraft.util.Formatting.RED), false);
                return TypedActionResult.fail(stack);
            } else if (currentType == targetStorageType) {
                // 相同类型，无需再次激活
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.storage_already_enabled")
                        .formatted(net.minecraft.util.Formatting.YELLOW), false);
                return TypedActionResult.fail(stack);
            }
        }
        
        // 检查是否有待确认的激活请求
        if (StorageActivationConfirmation.hasPendingConfirmation(serverPlayer)) {
            // 确认激活
            if (StorageActivationConfirmation.confirmActivation(serverPlayer)) {
                return performStorageActivation(serverPlayer, stack, hand, config, targetStorageType);
            } else {
                // 确认超时，取消激活
                StorageActivationConfirmation.cancelPendingConfirmation(serverPlayer);
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.activation_timeout")
                        .formatted(net.minecraft.util.Formatting.RED), false);
                return TypedActionResult.fail(stack);
            }
        }
        
        // 检查是否需要清空仓库数据，如果需要且仓库有数据，则要求确认
        if (config.isClearStorageOnEnable() && !access.portableStorage$getInventory().isEmpty()) {
            // 设置待确认状态
            StorageActivationConfirmation.setPendingConfirmation(serverPlayer);
            
            // 发送确认消息
            player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.confirm_activation")
                    .formatted(net.minecraft.util.Formatting.YELLOW), false);
            player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".message.confirm_activation_hint")
                    .formatted(net.minecraft.util.Formatting.GRAY), false);
            
            return TypedActionResult.fail(stack);
        }
        
        // 直接激活（仓库为空或不需要清空数据）
        return performStorageActivation(serverPlayer, stack, hand, config, targetStorageType);
    }
    
    /**
     * 执行仓库激活流程
     */
    private static TypedActionResult<ItemStack> performStorageActivation(ServerPlayerEntity player, ItemStack stack, Hand hand, ServerConfig config, StorageType storageType) {
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        
        // 记录升级前的状态
        boolean wasAlreadyEnabled = access.portableStorage$isStorageEnabled();
        StorageType previousType = wasAlreadyEnabled ? access.portableStorage$getStorageType() : null;
        
        // 启用玩家随身仓库
        access.portableStorage$setStorageEnabled(true);
        
        // 设置仓库类型
        access.portableStorage$setStorageType(storageType);
        
        // 检查是否需要清空仓库数据（仅在首次激活时清空，升级时不清空）
        if (config.isClearStorageOnEnable() && !wasAlreadyEnabled) {
            access.portableStorage$getInventory().clear();
            PortableStorage.LOGGER.info("Cleared storage data for player {} when enabling storage", 
                player.getName().getString());

            // 同步清空升级槽位与流体/垃圾槽位
            try {
                UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
                // 清空基础槽位 0..4
                for (int i = 0; i < upgrades.getBaseSlotCount(); i++) {
                    upgrades.setStack(i, net.minecraft.item.ItemStack.EMPTY);
                }
                // 清空扩展槽位
                upgrades.clearExtendedSlots();
                // 清空垃圾槽位
                upgrades.setTrashSlot(net.minecraft.item.ItemStack.EMPTY);
                // 清空流体槽位与单位
                upgrades.setFluidStack(net.minecraft.item.ItemStack.EMPTY);
                upgrades.setFluidUnits("lava", 0);
                upgrades.setFluidUnits("water", 0);
                upgrades.setFluidUnits("milk", 0);
                // 同步到客户端
                ServerNetworkingHandlers.sendUpgradeSync(player);
                ServerNetworkingHandlers.sendSync(player);
            } catch (Throwable ignored) {}
        }
        
        // 检查是否需要消耗道具
        boolean shouldConsume = false;
        if (storageType == StorageType.FULL && config.isConsumeEnableItem()) {
            shouldConsume = true;
        } else if (storageType == StorageType.PRIMARY && config.isConsumePrimaryStorageItem()) {
            shouldConsume = true;
        }
        
        if (shouldConsume) {
            if (stack.getCount() > 1) {
                stack.decrement(1);
            } else {
                player.setStackInHand(hand, ItemStack.EMPTY);
            }
        }
        
        // 发送成功消息
        String itemName = stack.getItem().getName().getString();
        String messageKey;
        
        // 检查是否为升级情况
        if (wasAlreadyEnabled && previousType == StorageType.PRIMARY && storageType == StorageType.FULL) {
            // 从初级仓库升级到完整仓库
            messageKey = PortableStorage.MOD_ID + ".message.storage_upgraded";
        } else {
            // 正常激活
            messageKey = storageType == StorageType.PRIMARY ? 
                PortableStorage.MOD_ID + ".message.primary_storage_enabled" : 
                PortableStorage.MOD_ID + ".message.storage_enabled";
        }
        
        player.sendMessage(Text.translatable(messageKey, itemName)
                .formatted(net.minecraft.util.Formatting.GREEN), false);
        
        // 如果是升级，同步数据到客户端
        if (wasAlreadyEnabled && previousType == StorageType.PRIMARY && storageType == StorageType.FULL) {
            ServerNetworkingHandlers.sendUpgradeSync(player);
            ServerNetworkingHandlers.sendSync(player);
            ServerNetworkingHandlers.sendEnablementSync(player); // 同步存储类型更新
        }
        
        PortableStorage.LOGGER.info("Player {} enabled portable storage with item {}", 
            player.getName().getString(), config.getEnableItem());
        
        return TypedActionResult.success(player.getStackInHand(hand));
    }
}
