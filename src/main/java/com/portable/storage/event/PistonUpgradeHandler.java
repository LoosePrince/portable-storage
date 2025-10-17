package com.portable.storage.event;

import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.net.ServerNetworkingHandlers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 活塞升级处理器
 * 当活塞升级激活且玩家不在任何界面时，自动从仓库补充主手和副手的物品消耗
 */
public class PistonUpgradeHandler {
    
    private static final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private static final long CHECK_INTERVAL = 6L; // 每6tick（0.3秒）检查一次
    
    // 存储每个玩家上次的主手和副手物品状态
    private static final Map<UUID, ItemStack> lastMainHandItems = new HashMap<>();
    private static final Map<UUID, ItemStack> lastOffHandItems = new HashMap<>();
    // 存储每个玩家上次的主手槽位索引
    private static final Map<UUID, Integer> lastMainHandSlot = new HashMap<>();
    
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long currentTime = server.getTicks();
            
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                Long lastCheck = lastCheckTime.get(playerId);
                
                // 检查是否需要检查这个玩家
                if (lastCheck == null || currentTime - lastCheck >= CHECK_INTERVAL) {
                    checkAndRefillHands(player);
                    lastCheckTime.put(playerId, currentTime);
                }
            }
        });
    }
    
    /**
     * 检查并补充主手和副手物品
     */
    private static void checkAndRefillHands(ServerPlayerEntity player) {
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        UUID playerId = player.getUuid();
        
        // 检查活塞升级是否激活
        if (!upgrades.isPistonUpgradeActive()) {
            // 如果升级被禁用，清除该玩家的补充记录
            cleanupPlayer(playerId);
            return;
        }
        
        // 检查主手物品
        ItemStack currentMainHand = player.getMainHandStack();
        ItemStack lastMainHand = lastMainHandItems.get(playerId);
        int currentMainHandSlot = player.getInventory().selectedSlot;
        Integer lastMainHandSlotIndex = lastMainHandSlot.get(playerId);
        
        // 检查主手槽位是否发生变化
        boolean mainHandSlotChanged = lastMainHandSlotIndex != null && lastMainHandSlotIndex != currentMainHandSlot;
        
        if (!mainHandSlotChanged && shouldRefillSlot(currentMainHand, lastMainHand)) {
            // 如果当前槽位为空，使用上次记录的物品作为目标
            ItemStack targetStack = currentMainHand.isEmpty() ? lastMainHand : currentMainHand;
            refillFromStorage(player, targetStack, Hand.MAIN_HAND);
        }
        lastMainHandItems.put(playerId, currentMainHand.copy());
        lastMainHandSlot.put(playerId, currentMainHandSlot);
        
        // 检查副手物品
        ItemStack currentOffHand = player.getOffHandStack();
        ItemStack lastOffHand = lastOffHandItems.get(playerId);
        
        if (shouldRefillSlot(currentOffHand, lastOffHand)) {
            // 如果当前槽位为空，使用上次记录的物品作为目标
            ItemStack targetStack = currentOffHand.isEmpty() ? lastOffHand : currentOffHand;
            refillFromStorage(player, targetStack, Hand.OFF_HAND);
        }
        lastOffHandItems.put(playerId, currentOffHand.copy());
    }
    
    /**
     * 判断是否需要补充物品
     */
    private static boolean shouldRefillSlot(ItemStack current, ItemStack last) {
        if (last == null || last.isEmpty()) {
            return false; // 没有上次记录，不补充
        }
        
        // 情况1：当前槽位为空，但上次有物品（物品被完全消耗）
        if (current.isEmpty()) {
            return true; // 需要补充
        }
        
        // 情况2：当前槽位有物品，检查是否与上次相同
        if (!ItemStack.areItemsAndComponentsEqual(current, last)) {
            return false; // 物品不同，不补充
        }
        
        // 情况3：物品相同，检查数量是否减少
        if (current.getCount() < last.getCount()) {
            // 对于可堆叠物品，仅在低于半组时才触发补充
            int maxCount = current.getMaxCount();
            if (maxCount > 1) { // 可堆叠物品
                int halfStack = maxCount / 2;
                return current.getCount() < halfStack;
            } else { // 无法堆叠物品（如工具、不死图腾等）
                return true; // 直接补充
            }
        }
        
        return false;
    }
    
    /**
     * 从仓库补充物品到指定手部
     */
    private static void refillFromStorage(ServerPlayerEntity player, ItemStack targetStack, Hand hand) {
        if (targetStack.isEmpty()) {
            return;
        }
        
        ItemStack currentStack = hand == Hand.MAIN_HAND ? player.getMainHandStack() : player.getOffHandStack();
        
        // 计算需要补充的数量
        int currentCount = currentStack.isEmpty() ? 0 : currentStack.getCount();
        int maxCount = targetStack.getMaxCount();
        int needCount = maxCount - currentCount;
        
        if (needCount <= 0) {
            return;
        }
        
        // 从仓库获取物品
        long availableCount = ServerNetworkingHandlers.getAvailableCount(player, targetStack);
        if (availableCount <= 0) {
            return;
        }
        
        // 计算实际可以补充的数量
        int actualTake = (int) Math.min(needCount, availableCount);
        
        // 从仓库取出物品
        long taken = ServerNetworkingHandlers.takeFromStorage(player, targetStack, actualTake);
        if (taken <= 0) {
            return;
        }
        
        // 补充到玩家手中
        if (currentStack.isEmpty()) {
            // 空槽位：直接放入新物品
            ItemStack newStack = targetStack.copy();
            newStack.setCount((int) taken);
            
            if (hand == Hand.MAIN_HAND) {
                player.setStackInHand(Hand.MAIN_HAND, newStack);
            } else {
                player.setStackInHand(Hand.OFF_HAND, newStack);
            }
        } else {
            // 非空槽位：增加数量
            ItemStack newStack = currentStack.copy();
            newStack.setCount(currentCount + (int) taken);
            
            if (hand == Hand.MAIN_HAND) {
                player.setStackInHand(Hand.MAIN_HAND, newStack);
            } else {
                player.setStackInHand(Hand.OFF_HAND, newStack);
            }
        }
        
        // 发送同步消息
        ServerNetworkingHandlers.sendSync(player);
        
        // 显示补充提示消息
        showRefillMessage(player, targetStack, (int) taken, availableCount - taken);
    }
    
    /**
     * 显示补充提示消息
     */
    private static void showRefillMessage(ServerPlayerEntity player, ItemStack itemStack, int refilledCount, long remainingCount) {
        if (refilledCount <= 0) {
            return;
        }
        
        // 获取物品名称
        String itemName = itemStack.getName().getString();
        
        // 构建消息
        net.minecraft.text.Text message = net.minecraft.text.Text.translatable(
            "portable_storage.piston.refill_message", 
            refilledCount, 
            itemName, 
            remainingCount
        );
        
        // 发送消息到物品栏上方
        player.sendMessage(message, true);
    }
    
    /**
     * 清理玩家数据（当玩家离线时）
     */
    public static void cleanupPlayer(UUID playerId) {
        lastCheckTime.remove(playerId);
        lastMainHandItems.remove(playerId);
        lastOffHandItems.remove(playerId);
        lastMainHandSlot.remove(playerId);
    }
}

