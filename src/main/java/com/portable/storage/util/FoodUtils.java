package com.portable.storage.util;

import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.FilterRuleManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 食物处理工具类
 * 提供获取食物信息、判断食物、消耗食物等功能
 */
public class FoodUtils {
    
    /**
     * 检查物品是否是食物
     */
    public static boolean isFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        // 检查物品是否有食物组件
        return stack.get(DataComponentTypes.FOOD) != null;
    }
    
    /**
     * 检查物品是否可以被食用
     */
    public static boolean isEdible(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        // 检查物品是否有食物组件
        return stack.get(DataComponentTypes.FOOD) != null;
    }
    
    /**
     * 获取食物信息
     */
    public static FoodInfo getFoodInfo(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        
        FoodComponent foodComponent = stack.get(DataComponentTypes.FOOD);
        if (foodComponent == null) {
            return null;
        }
        
        return new FoodInfo(
            foodComponent.nutrition(),           // 饥饿值
            foodComponent.saturation(),          // 饱和度
            foodComponent.canAlwaysEat(),        // 是否总是可以吃（通常是肉类）
            foodComponent.eatSeconds() < 1.0f    // 是否为快餐
        );
    }
    
    /**
     * 检查玩家是否需要进食（饱食度低于指定值）
     */
    public static boolean needsFood(ServerPlayerEntity player, int threshold) {
        HungerManager hungerManager = player.getHungerManager();
        return hungerManager.getFoodLevel() < threshold;
    }
    
    /**
     * 获取玩家当前饱食度
     */
    public static int getCurrentFoodLevel(ServerPlayerEntity player) {
        HungerManager hungerManager = player.getHungerManager();
        return hungerManager.getFoodLevel();
    }
    
    /**
     * 获取玩家当前饱和度
     */
    public static float getCurrentSaturationLevel(ServerPlayerEntity player) {
        HungerManager hungerManager = player.getHungerManager();
        return hungerManager.getSaturationLevel();
    }
    
    /**
     * 消耗食物为玩家回复饱食度
     */
    public static boolean consumeFoodForPlayer(ServerPlayerEntity player, ItemStack foodStack) {
        if (foodStack.isEmpty() || !isFood(foodStack)) {
            return false;
        }
        
        FoodComponent foodComponent = foodStack.get(DataComponentTypes.FOOD);
        if (foodComponent == null) {
            return false;
        }
        
        // 检查玩家是否可以吃这个食物
        if (!player.canConsume(false)) {
            return false;
        }
        
        // 获取食物信息
        int nutrition = foodComponent.nutrition();
        float saturation = foodComponent.saturation();
        
        // 恢复饥饿值
        HungerManager hungerManager = player.getHungerManager();
        hungerManager.add(nutrition, saturation);
        
        // 消耗物品
        if (!player.getAbilities().creativeMode) {
            foodStack.decrement(1);
        }
        
        return true;
    }
    
    /**
     * 从仓库中自动寻找并消耗数量最多的食物
     * 直到饱食度达到目标值
     */
    public static AutoEatResult autoEatFromStorage(ServerPlayerEntity player, StorageInventory storage, int targetFoodLevel) {
        if (!needsFood(player, targetFoodLevel)) {
            return new AutoEatResult(false, 0, 0, null);
        }
        
        int initialFoodLevel = getCurrentFoodLevel(player);
        int totalConsumed = 0;
        String lastFoodName = null;
        
        // 循环进食直到达到目标饱食度
        while (needsFood(player, targetFoodLevel)) {
            // 寻找数量最多的食物（应用筛选逻辑）
            int bestFoodIndex = findMostAbundantFoodIndex(storage, player);
            if (bestFoodIndex == -1) {
                break; // 没有食物了
            }
            
            // 先获取食物信息（在取出之前）
            ItemStack templateStack = storage.getDisplayStack(bestFoodIndex);
            if (templateStack.isEmpty() || !isFood(templateStack)) {
                break; // 不是食物，跳过
            }
            
            // 先获取食物名称（在取出之前）
            String foodName = templateStack.getName().getString();
            
            // 从仓库中取出1个食物
            long taken = storage.takeByIndex(bestFoodIndex, 1, System.currentTimeMillis());
            
            if (taken > 0) {
                // 使用模板堆栈来消耗食物
                ItemStack foodStack = templateStack.copy();
                foodStack.setCount(1);
                
                // 消耗食物
                if (consumeFoodForPlayer(player, foodStack)) {
                    totalConsumed++;
                    lastFoodName = foodName; // 使用之前获取的名称
                }
            } else {
                break; // 无法取出食物，停止
            }
        }
        
        int finalFoodLevel = getCurrentFoodLevel(player);
        boolean success = finalFoodLevel >= targetFoodLevel;
        
        return new AutoEatResult(success, totalConsumed, finalFoodLevel - initialFoodLevel, lastFoodName);
    }
    
    /**
     * 从新版存储系统中自动寻找并消耗数量最多的食物
     * 直到饱食度达到目标值
     */
    public static AutoEatResult autoEatFromNewStore(ServerPlayerEntity player, int targetFoodLevel) {
        if (!needsFood(player, targetFoodLevel)) {
            return new AutoEatResult(false, 0, 0, null);
        }
        
        int initialFoodLevel = getCurrentFoodLevel(player);
        int totalConsumed = 0;
        String lastFoodName = null;
        
        // 循环进食直到达到目标饱食度
        while (needsFood(player, targetFoodLevel)) {
            // 获取合并的存储视图（每次循环都重新获取，确保数据最新）
            var storage = com.portable.storage.net.ServerNetworkingHandlers.buildMergedSnapshot(player);
            
            // 寻找数量最多的食物（应用筛选逻辑）
            int bestFoodIndex = findMostAbundantFoodIndex(storage, player);
            if (bestFoodIndex == -1) {
                break; // 没有食物了
            }
            
            // 先获取食物信息（在取出之前）
            ItemStack templateStack = storage.getDisplayStack(bestFoodIndex);
            if (templateStack.isEmpty() || !isFood(templateStack)) {
                break; // 不是食物，跳过
            }
            
            // 先获取食物名称（在取出之前）
            String foodName = templateStack.getName().getString();
            
            // 使用新版存储服务直接从存储中移除物品
            long taken = com.portable.storage.newstore.NewStoreService.takeForOnlinePlayer(player, templateStack, 1);
            
            if (taken > 0) {
                // 使用模板堆栈来消耗食物
                ItemStack foodStack = templateStack.copy();
                foodStack.setCount(1);
                
                // 消耗食物
                if (consumeFoodForPlayer(player, foodStack)) {
                    totalConsumed++;
                    lastFoodName = foodName; // 使用之前获取的名称
                }
            } else {
                break; // 无法取出食物，停止
            }
        }
        
        int finalFoodLevel = getCurrentFoodLevel(player);
        boolean success = finalFoodLevel >= targetFoodLevel;
        
        return new AutoEatResult(success, totalConsumed, finalFoodLevel - initialFoodLevel, lastFoodName);
    }
    
    /**
     * 在仓库中寻找数量最多的食物的索引（应用筛选逻辑）
     */
    private static int findMostAbundantFoodIndex(StorageInventory storage, ServerPlayerEntity player) {
        int bestIndex = -1;
        long maxCount = 0;
        
        int capacity = storage.getCapacity();
        
        // 遍历仓库中的所有变体
        for (int i = 0; i < capacity; i++) {
            ItemStack stack = storage.getDisplayStack(i);
            long count = storage.getCountByIndex(i);
            
            if (!stack.isEmpty() && count > 0) {
                boolean isFoodItem = isFood(stack);
                
                // 应用筛选逻辑：只有符合筛选规则的食物才会被选中
                if (isFoodItem && FilterRuleManager.shouldAutoEatItem(player, stack) && count > maxCount) {
                    maxCount = count;
                    bestIndex = i;
                }
            }
        }
        
        return bestIndex;
    }
    
    /**
     * 食物信息类
     */
    public static class FoodInfo {
        public final int nutrition;        // 饥饿值恢复量
        public final float saturation;     // 饱和度恢复量
        public final boolean isMeat;       // 是否为肉类
        public final boolean isFastFood;   // 是否为快餐（如金胡萝卜）
        
        public FoodInfo(int nutrition, float saturation, boolean isMeat, boolean isFastFood) {
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.isMeat = isMeat;
            this.isFastFood = isFastFood;
        }
    }
    
    /**
     * 自动进食结果类
     */
    public static class AutoEatResult {
        public final boolean success;          // 是否成功达到目标饱食度
        public final int itemsConsumed;        // 消耗的物品数量
        public final int foodRestored;         // 恢复的饱食度
        public final String lastFoodName;      // 最后消耗的食物名称
        
        public AutoEatResult(boolean success, int itemsConsumed, int foodRestored, String lastFoodName) {
            this.success = success;
            this.itemsConsumed = itemsConsumed;
            this.foodRestored = foodRestored;
            this.lastFoodName = lastFoodName;
        }
    }
}
