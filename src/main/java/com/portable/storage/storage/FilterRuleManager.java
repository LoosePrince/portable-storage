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
            // 自动进食规则独立于漏斗拾取规则，但默认继承一份以兼容旧配置
            if (autoEatRules == null || autoEatRules.isEmpty()) {
                this.autoEatRules = new ArrayList<>(filterRules);
            } else {
                this.autoEatRules = new ArrayList<>(autoEatRules);
            }
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
        int autoEatSize = (autoEatRules != null ? autoEatRules.size() : filterRules.size());
        PortableStorage.LOGGER.info("Player {} synced {} filter rules, {} destroy rules and {} auto-eat rules", 
            player.getName().getString(), filterRules.size(), destroyRules.size(), autoEatSize);
    }
    
    /**
     * 获取玩家的筛选规则
     */
    public static PlayerFilterRules getPlayerRules(ServerPlayerEntity player) {
        return playerRules.get(player.getUuid());
    }
    
    /**
     * 检查物品是否应该被漏斗拾取（漏斗拾取筛选规则）
     * @param player 玩家
     * @param itemStack 物品
     * @return true=应该拾取, false=不应该拾取
     */
    public static boolean shouldPickupItem(ServerPlayerEntity player, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        PlayerFilterRules rules = playerRules.get(player.getUuid());
        if (rules == null) {
            // 没有任何规则时拾取全部
            return true;
        }
        
        return evaluateFilterRules(itemStack, rules.filterRules, true);
    }
    
    /**
     * 检查物品是否应该作为自动进食候选（自动进食筛选规则）
     * @param player 玩家
     * @param itemStack 物品
     * @return true=可以作为自动进食候选, false=不允许自动进食
     */
    public static boolean shouldAutoEatItem(ServerPlayerEntity player, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        
        PlayerFilterRules rules = playerRules.get(player.getUuid());
        if (rules == null) {
            // 没有任何规则时允许全部食物参与自动进食
            return true;
        }
        
        return evaluateFilterRules(itemStack, rules.autoEatRules, true);
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
     * 通用规则评估逻辑（白名单/黑名单组合）
     * @param itemStack 待检查物品
     * @param rules 规则列表
     * @param defaultIfNoRules 当没有任何启用规则时的默认结果
     */
    private static boolean evaluateFilterRules(ItemStack itemStack, List<SyncFilterRulesC2SPayload.FilterRule> rules, boolean defaultIfNoRules) {
        if (rules == null || rules.isEmpty()) {
            return defaultIfNoRules;
        }
        
        // 分离白名单和黑名单规则
        java.util.List<SyncFilterRulesC2SPayload.FilterRule> whitelistRules = new java.util.ArrayList<>();
        java.util.List<SyncFilterRulesC2SPayload.FilterRule> blacklistRules = new java.util.ArrayList<>();
        
        for (SyncFilterRulesC2SPayload.FilterRule rule : rules) {
            if (!rule.enabled) continue;
            
            if (rule.isWhitelist) {
                whitelistRules.add(rule);
            } else {
                blacklistRules.add(rule);
            }
        }
        
        // 1. 先检查黑名单：如果匹配任何黑名单规则，则不通过
        for (SyncFilterRulesC2SPayload.FilterRule rule : blacklistRules) {
            if (matchesRuleDirect(itemStack, rule)) {
                return false;
            }
        }
        
        // 2. 如果有白名单规则，必须匹配至少一个白名单规则
        if (!whitelistRules.isEmpty()) {
            for (SyncFilterRulesC2SPayload.FilterRule rule : whitelistRules) {
                if (matchesRuleDirect(itemStack, rule)) {
                    return true;
                }
            }
            return false;
        }
        
        // 3. 如果没有白名单规则，只有黑名单规则，则通过黑名单检查即可通过
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
        net.minecraft.network.PacketByteBuf b = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        com.portable.storage.net.payload.RequestFilterRulesSyncS2CPayload.write(b, new com.portable.storage.net.payload.RequestFilterRulesSyncS2CPayload());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
            player,
            com.portable.storage.net.payload.RequestFilterRulesSyncS2CPayload.ID,
            b
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
