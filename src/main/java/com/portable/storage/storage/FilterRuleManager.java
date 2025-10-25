package com.portable.storage.storage;

import com.portable.storage.PortableStorage;
import com.portable.storage.net.payload.SyncFilterRulesC2SPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
        
        public PlayerFilterRules(List<SyncFilterRulesC2SPayload.FilterRule> filterRules, 
                                List<SyncFilterRulesC2SPayload.FilterRule> destroyRules) {
            this.filterRules = new ArrayList<>(filterRules);
            this.destroyRules = new ArrayList<>(destroyRules);
        }
    }
    
    /**
     * 同步玩家的筛选规则
     */
    public static void syncPlayerRules(ServerPlayerEntity player, 
                                     List<SyncFilterRulesC2SPayload.FilterRule> filterRules,
                                     List<SyncFilterRulesC2SPayload.FilterRule> destroyRules) {
        UUID playerId = player.getUuid();
        playerRules.put(playerId, new PlayerFilterRules(filterRules, destroyRules));
        
        // 记录日志
        PortableStorage.LOGGER.info("Player {} synced {} filter rules and {} destroy rules", 
            player.getName().getString(), filterRules.size(), destroyRules.size());
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
        if (rules == null || rules.filterRules.isEmpty()) {
            // 没有筛选规则时拾取全部
            return true;
        }
        
        // 分离白名单和黑名单规则
        java.util.List<SyncFilterRulesC2SPayload.FilterRule> whitelistRules = new java.util.ArrayList<>();
        java.util.List<SyncFilterRulesC2SPayload.FilterRule> blacklistRules = new java.util.ArrayList<>();
        
        for (SyncFilterRulesC2SPayload.FilterRule rule : rules.filterRules) {
            if (!rule.enabled) continue;
            
            if (rule.isWhitelist) {
                whitelistRules.add(rule);
            } else {
                blacklistRules.add(rule);
            }
        }
        
        // 1. 先检查黑名单：如果匹配任何黑名单规则，则不拾取
        for (SyncFilterRulesC2SPayload.FilterRule rule : blacklistRules) {
            if (matchesRuleDirect(itemStack, rule)) {
                return false; // 匹配黑名单，不拾取
            }
        }
        
        // 2. 如果有白名单规则，必须匹配至少一个白名单规则
        if (!whitelistRules.isEmpty()) {
            for (SyncFilterRulesC2SPayload.FilterRule rule : whitelistRules) {
                if (matchesRuleDirect(itemStack, rule)) {
                    return true; // 匹配白名单，拾取
                }
            }
            return false; // 没有匹配任何白名单，不拾取
        }
        
        // 3. 如果没有白名单规则，只有黑名单规则，则通过黑名单检查即可拾取
        return true;
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
     * 检查物品是否匹配规则
     */
    private static boolean matchesRule(ItemStack itemStack, SyncFilterRulesC2SPayload.FilterRule rule) {
        String itemId = Registries.ITEM.getId(itemStack.getItem()).toString();
        String itemName = itemStack.getName().getString();
        String modId = Registries.ITEM.getId(itemStack.getItem()).getNamespace();
        
        boolean matches = false;
        
        try {
            // 尝试正则表达式匹配
            Pattern pattern = Pattern.compile(rule.matchRule, Pattern.CASE_INSENSITIVE);
            matches = pattern.matcher(itemId).find() || 
                     pattern.matcher(itemName).find() ||
                     pattern.matcher(modId).find();
        } catch (PatternSyntaxException e) {
            // 正则表达式无效，使用简单字符串匹配
            String lowerRule = rule.matchRule.toLowerCase();
            matches = itemId.toLowerCase().contains(lowerRule) ||
                     itemName.toLowerCase().contains(lowerRule) ||
                     modId.toLowerCase().contains(lowerRule);
        }
        
        // 根据白名单/黑名单模式返回结果
        if (rule.isWhitelist) {
            // 白名单：匹配则通过
            return matches;
        } else {
            // 黑名单：匹配则排除
            return !matches;
        }
    }
    
    /**
     * 获取玩家的规则数量统计
     */
    public static String getPlayerRulesStats(ServerPlayerEntity player) {
        PlayerFilterRules rules = playerRules.get(player.getUuid());
        if (rules == null) {
            return "无规则";
        }
        
        int enabledFilters = (int) rules.filterRules.stream().filter(r -> r.enabled).count();
        int enabledDestroys = (int) rules.destroyRules.stream().filter(r -> r.enabled).count();
        
        return String.format("筛选规则: %d/%d, 销毁规则: %d/%d", 
            enabledFilters, rules.filterRules.size(),
            enabledDestroys, rules.destroyRules.size());
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
}
