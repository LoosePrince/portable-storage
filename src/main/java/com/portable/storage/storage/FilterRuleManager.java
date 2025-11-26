package com.portable.storage.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.portable.storage.PortableStorage;
import com.portable.storage.blockentity.BoundBarrelBlockEntity;
import com.portable.storage.net.payload.SyncFilterRulesC2SPayload;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 服务器端筛选规则管理器
 * 管理玩家的筛选和销毁规则
 */
public class FilterRuleManager {
    private static final Map<UUID, PlayerFilterRules> playerRules = new ConcurrentHashMap<>();
    
    /**
     * 玩家筛选规则
     */
    public static class PlayerFilterRules {
        public final List<SyncFilterRulesC2SPayload.FilterRule> filterRules;
        public final List<SyncFilterRulesC2SPayload.FilterRule> destroyRules;
        public final List<SyncFilterRulesC2SPayload.FilterRule> autoEatRules;
        
        public PlayerFilterRules(List<SyncFilterRulesC2SPayload.FilterRule> filterRules, 
                                 List<SyncFilterRulesC2SPayload.FilterRule> destroyRules,
                                 List<SyncFilterRulesC2SPayload.FilterRule> autoEatRules) {
            this.filterRules = new ArrayList<>(filterRules);
            this.destroyRules = new ArrayList<>(destroyRules);
            this.autoEatRules = new ArrayList<>(autoEatRules);
        }
    }
    
    /**
     * 同步玩家的筛选规则
     */
    public static void syncPlayerRules(ServerPlayerEntity player, 
                                     List<SyncFilterRulesC2SPayload.FilterRule> filterRules,
                                     List<SyncFilterRulesC2SPayload.FilterRule> destroyRules,
                                     List<SyncFilterRulesC2SPayload.FilterRule> autoEatRules) {
        UUID playerId = player.getUuid();
        playerRules.put(playerId, new PlayerFilterRules(filterRules, destroyRules, autoEatRules));
        
        // 记录日志
        PortableStorage.LOGGER.info("Player {} synced pickup={}, destroy={}, autoEat={} rules", 
            player.getName().getString(), filterRules.size(), destroyRules.size(), autoEatRules.size());
    }
    
    /**
     * 获取玩家的筛选规则
     */
    public static PlayerFilterRules getPlayerRules(ServerPlayerEntity player) {
        return playerRules.get(player.getUuid());
    }
    
    /**
     * 检查物品是否应该被漏斗拾取
     * @param player 玩家
     * @param itemStack 物品
     * @return true=应该拾取, false=不应该拾取
     */
    public static boolean shouldPickupItem(ServerPlayerEntity player, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        PlayerFilterRules rules = playerRules.get(player.getUuid());
        return evaluateFilterRules(rules.filterRules, itemStack);
    }
    
    /**
     * 检查物品是否应该被销毁
     * @param player 玩家
     * @param itemStack 物品
     * @return true=应该销毁, false=不应该销毁
     */
    public static boolean shouldDestroyItem(ServerPlayerEntity player, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        PlayerFilterRules rules = playerRules.get(player.getUuid());
        if (rules == null || rules.destroyRules.isEmpty()) {
            // 没有销毁规则时不销毁
            return false;
        }
        
        // 检查销毁规则
        for (SyncFilterRulesC2SPayload.FilterRule rule : rules.destroyRules) {
            if (!rule.enabled) continue;
            
            if (matchesRuleDirect(itemStack, rule)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 检查物品是否允许用于自动喂食
     */
    public static boolean shouldAutoEatItem(ServerPlayerEntity player, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        PlayerFilterRules rules = playerRules.get(player.getUuid());
        if (rules == null) {
            return true;
        }
        return evaluateFilterRules(rules.autoEatRules, itemStack);
    }

    private static boolean evaluateFilterRules(List<SyncFilterRulesC2SPayload.FilterRule> sourceRules, ItemStack itemStack) {
        if (sourceRules == null || sourceRules.isEmpty()) {
            return true;
        }
        
        java.util.List<SyncFilterRulesC2SPayload.FilterRule> whitelistRules = new java.util.ArrayList<>();
        java.util.List<SyncFilterRulesC2SPayload.FilterRule> blacklistRules = new java.util.ArrayList<>();
        
        for (SyncFilterRulesC2SPayload.FilterRule rule : sourceRules) {
            if (!rule.enabled) continue;
            
            if (rule.isWhitelist) {
                whitelistRules.add(rule);
            } else {
                blacklistRules.add(rule);
            }
        }
        
        for (SyncFilterRulesC2SPayload.FilterRule rule : blacklistRules) {
            if (matchesRuleDirect(itemStack, rule)) {
                return false;
            }
        }
        
        if (!whitelistRules.isEmpty()) {
            for (SyncFilterRulesC2SPayload.FilterRule rule : whitelistRules) {
                if (matchesRuleDirect(itemStack, rule)) {
                    return true;
                }
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * 直接检查物品是否匹配规则（不考虑白名单/黑名单模式）
     */
    private static boolean matchesRuleDirect(ItemStack itemStack, SyncFilterRulesC2SPayload.FilterRule rule) {
        String itemId = Registries.ITEM.getId(itemStack.getItem()).toString();
        String itemName = itemStack.getName().getString();
        String modId = Registries.ITEM.getId(itemStack.getItem()).getNamespace();
        
        // 检查是否包含正则表达式特殊字符
        boolean isRegex = rule.matchRule.matches(".*[\\[\\](){}.*+?^$|\\\\].*");
        
        if (isRegex) {
            try {
                // 尝试正则表达式匹配
                Pattern pattern = Pattern.compile(rule.matchRule, Pattern.CASE_INSENSITIVE);
                return pattern.matcher(itemId).find() || 
                       pattern.matcher(itemName).find() ||
                       pattern.matcher(modId).find();
            } catch (PatternSyntaxException e) {
                // 正则表达式无效，回退到精确匹配
                return matchesExact(itemId, itemName, modId, rule.matchRule);
            }
        } else {
            // 使用精确匹配
            return matchesExact(itemId, itemName, modId, rule.matchRule);
        }
    }
    
    /**
     * 精确匹配逻辑
     */
    private static boolean matchesExact(String itemId, String itemName, String modId, String rule) {
        String lowerRule = rule.toLowerCase();
        
        // 1. 精确匹配物品ID
        if (itemId.toLowerCase().equals(lowerRule)) {
            return true;
        }
        
        // 2. 精确匹配模组ID
        if (modId.toLowerCase().equals(lowerRule)) {
            return true;
        }
        
        // 3. 检查是否匹配物品ID的末尾部分（支持 minecraft:stone 匹配 minecraft:stone）
        if (itemId.toLowerCase().endsWith(":" + lowerRule)) {
            return true;
        }
        
        // 4. 检查是否匹配物品名称
        if (itemName.toLowerCase().equals(lowerRule)) {
            return true;
        }
        
        // 5. 如果规则包含冒号，尝试完整匹配
        if (lowerRule.contains(":")) {
            return itemId.toLowerCase().equals(lowerRule);
        }
        
        return false;
    }
    
    /**
     * 清理玩家的规则（玩家离线时）
     */
    public static void clearPlayerRules(UUID playerId) {
        playerRules.remove(playerId);
    }
    
    /**
     * 请求玩家同步筛选规则
     */
    public static void requestRulesSync(ServerPlayerEntity player) {
        // 检查是否已经有规则，如果有则不需要请求同步
        PlayerFilterRules rules = playerRules.get(player.getUuid());
        if (rules != null) {
            return; // 已经有规则，不需要同步
        }
        
        // 发送请求同步的网络包
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
            player, 
            new com.portable.storage.net.payload.RequestFilterRulesSyncS2CPayload()
        );
        
        PortableStorage.LOGGER.debug("Requested filter rules sync for player {}", player.getName().getString());
    }
    
    /**
     * 检查物品是否应该被绑定木桶拾取
     */
    public static boolean shouldPickupItemForBarrel(BoundBarrelBlockEntity barrel, ItemStack itemStack) {
        return BarrelFilterRuleManager.shouldPickupItem(barrel, itemStack);
    }
}
