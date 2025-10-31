package com.portable.storage.storage;

import java.util.List;
import java.util.regex.Pattern;

import com.portable.storage.blockentity.BoundBarrelBlockEntity;
import com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 绑定木桶筛选规则管理器
 * 管理绑定木桶的筛选和销毁规则
 */
public class BarrelFilterRuleManager {
    
    /**
     * 检查物品是否应该被拾取（基于绑定木桶的筛选规则）
     */
    public static boolean shouldPickupItem(BoundBarrelBlockEntity barrel, ItemStack itemStack) {
        if (barrel == null || itemStack.isEmpty()) {
            return false;
        }
        
        List<FilterRule> filterRules = barrel.getFilterRules();
        
        // 如果没有筛选规则，拾取所有物品
        if (filterRules.isEmpty()) {
            return true;
        }
        
        // 分离白名单和黑名单规则
        List<FilterRule> whitelistRules = filterRules.stream()
            .filter(rule -> rule.enabled && rule.isWhitelist)
            .toList();
        List<FilterRule> blacklistRules = filterRules.stream()
            .filter(rule -> rule.enabled && !rule.isWhitelist)
            .toList();
        
        // 优先处理黑名单：如果匹配任何启用的黑名单规则，则不拾取
        for (FilterRule rule : blacklistRules) {
            if (matchesRuleDirect(rule.matchRule, itemStack)) {
                return false;
            }
        }
        
        // 如果有白名单规则，物品必须匹配至少一个启用的白名单规则
        if (!whitelistRules.isEmpty()) {
            for (FilterRule rule : whitelistRules) {
                if (matchesRuleDirect(rule.matchRule, itemStack)) {
                    return true;
                }
            }
            return false; // 没有匹配任何白名单规则
        }
        
        // 如果只有黑名单规则，且没有匹配黑名单，则拾取
        return true;
    }
    
    
    /**
     * 直接匹配规则（智能匹配：自动检测正则表达式或精确匹配）
     */
    private static boolean matchesRuleDirect(String rule, ItemStack itemStack) {
        if (rule == null || rule.isEmpty()) {
            return false;
        }
        
        // 检查是否包含正则表达式特殊字符
        boolean isRegex = rule.contains(".*") || rule.contains("^") || rule.contains("$") || 
                         rule.contains("[") || rule.contains("]") || rule.contains("+") || 
                         rule.contains("?") || rule.contains("|") || rule.contains("(") || 
                         rule.contains(")") || rule.contains("{") || rule.contains("}");
        
        if (isRegex) {
            // 使用正则表达式匹配
            try {
                Pattern pattern = Pattern.compile(rule, Pattern.CASE_INSENSITIVE);
                return matchesRegex(pattern, itemStack);
            } catch (Exception e) {
                // 正则表达式无效，回退到精确匹配
                return matchesExact(rule, itemStack);
            }
        } else {
            // 使用精确匹配
            return matchesExact(rule, itemStack);
        }
    }
    
    /**
     * 正则表达式匹配
     */
    private static boolean matchesRegex(Pattern pattern, ItemStack itemStack) {
        // 匹配物品ID
        Identifier itemId = Registries.ITEM.getId(itemStack.getItem());
        if (pattern.matcher(itemId.toString()).find()) {
            return true;
        }
        
        // 匹配模组ID
        String modId = itemId.getNamespace();
        if (pattern.matcher(modId).find()) {
            return true;
        }
        
        // 匹配物品名称
        String itemName = itemStack.getName().getString();
        if (pattern.matcher(itemName).find()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 精确匹配
     */
    private static boolean matchesExact(String rule, ItemStack itemStack) {
        // 匹配物品ID
        Identifier itemId = Registries.ITEM.getId(itemStack.getItem());
        if (itemId.toString().equals(rule)) {
            return true;
        }
        
        // 匹配模组ID
        String modId = itemId.getNamespace();
        if (modId.equals(rule)) {
            return true;
        }
        
        // 匹配物品ID后缀（如 "stone" 匹配 "minecraft:stone"）
        String itemPath = itemId.getPath();
        if (itemPath.equals(rule)) {
            return true;
        }
        
        // 匹配物品名称
        String itemName = itemStack.getName().getString();
        if (itemName.equals(rule)) {
            return true;
        }
        
        return false;
    }
}
