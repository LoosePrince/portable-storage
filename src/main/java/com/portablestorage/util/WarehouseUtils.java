package com.portablestorage.util;

public class WarehouseUtils {
    /**
     * 格式化大额物品数量（k/M/G）
     */
    public static String formatCount(long count) {
        if (count == Long.MAX_VALUE) return "∞";
        
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
}
