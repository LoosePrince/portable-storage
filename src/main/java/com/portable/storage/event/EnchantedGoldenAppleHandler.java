package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.storage.AutoEatMode;
import com.portable.storage.util.FoodUtils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 附魔金苹果升级处理器
 * 当附魔金苹果升级激活且玩家饱食度低于14时，自动从仓库中食用数量最多的食物
 */
public class EnchantedGoldenAppleHandler {
    
    private static final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private static final long CHECK_INTERVAL = 20L; // 每20tick（1秒）检查一次
    
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long currentTime = server.getTicks();
            
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                Long lastCheck = lastCheckTime.get(playerId);
                
                // 检查是否需要检查这个玩家
                if (lastCheck == null || currentTime - lastCheck >= CHECK_INTERVAL) {
                    checkAndAutoEat(player);
                    lastCheckTime.put(playerId, currentTime);
                }
            }
        });
    }
    
    /**
     * 检查并自动进食
     */
    private static void checkAndAutoEat(ServerPlayerEntity player) {
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        
        // 检查仓库是否启用
        com.portable.storage.player.PlayerStorageAccess access = (com.portable.storage.player.PlayerStorageAccess) player;
        if (!access.portableStorage$isStorageEnabled()) {
            return;
        }
        
        // 检查附魔金苹果升级是否激活
        if (!upgrades.isEnchantedGoldenAppleUpgradeActive()) {
            return;
        }
        
        // 获取当前自动进食模式
        AutoEatMode currentMode = ServerNetworkingHandlers.getPlayerAutoEatMode(player);
        int threshold = currentMode.getThreshold();
        
        // 检查玩家饱食度是否低于阈值
        if (!FoodUtils.needsFood(player, threshold)) {
            return;
        }
        
        // 执行自动进食（使用新版存储系统）
        var result = FoodUtils.autoEatFromNewStore(player, threshold);
        
        // 发送消息给玩家
        if (result.success) {
            if (result.itemsConsumed > 0) {
                String foodName = result.lastFoodName != null ? result.lastFoodName : "食物";
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".enchanted_golden_apple.auto_eat", 
                    result.itemsConsumed, foodName, result.foodRestored), true);
            }
        } else {
            // 没有找到食物
            player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".enchanted_golden_apple.no_food"), true);
        }
    }
    
}
