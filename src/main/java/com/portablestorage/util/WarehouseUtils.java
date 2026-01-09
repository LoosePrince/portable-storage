package com.portablestorage.util;

import net.minecraft.world.item.ItemStack;

/**
 * 仓库工具类
 * 提供仓库相关的工具方法，如数量格式化、功能检查等
 */
public class WarehouseUtils {
    /**
     * 格式化大额物品数量
     * @param count 物品数量
     * @return 格式化后的字符串（如：1.5k、2.3M、∞）
     */
    public static String formatCount(long count) {
        if (count == Long.MAX_VALUE || count >= WarehouseConstants.INFINITE_COUNT) return "∞";
        
        String[] units = {"", "k", "M", "G", "T", "P", "E"};
        double value = count;
        int unitIndex = 0;
        
        while (value >= 1000 && unitIndex < units.length - 1) {
            value /= 1000;
            unitIndex++;
        }
        
        if (unitIndex == 0) {
            return String.format("%d", (long)value);
        } else {
            // 向下取整到一位小数
            double flooredValue = Math.floor(value * 10) / 10.0;
            return String.format("%.1f%s", flooredValue, units[unitIndex]);
        }
    }

    /**
     * 检查 3x3 合成是否启用
     * 需要服务端配置开启且玩家安装了工作台升级
     * @param player 玩家实例
     * @return 是否启用
     */
    public static boolean is3x3Enabled(net.minecraft.world.entity.player.Player player) {
        if (player == null) return false;
        if (!com.portablestorage.config.ModConfig.is3x3Enabled()) return false;
        
        var warehouse = com.portablestorage.component.ModComponents.get(player).getWarehouse(player.getUUID());
        ItemStack stack = warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID);
        return !stack.isEmpty() && com.portablestorage.upgrade.WorkbenchUpgrade.is3x3Enabled(stack);
    }
}
