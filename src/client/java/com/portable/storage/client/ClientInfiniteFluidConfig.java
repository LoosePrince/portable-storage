package com.portable.storage.client;

/**
 * 客户端无限流体配置
 * 存储从服务端同步的无限流体配置
 */
public class ClientInfiniteFluidConfig {
    private static boolean enableInfiniteLava = false;
    private static boolean enableInfiniteWater = false;
    private static int infiniteLavaThreshold = 10000;
    private static int infiniteWaterThreshold = 2;
    
    /**
     * 更新配置
     */
    public static void updateConfig(boolean enableLava, boolean enableWater, int lavaThreshold, int waterThreshold) {
        enableInfiniteLava = enableLava;
        enableInfiniteWater = enableWater;
        infiniteLavaThreshold = lavaThreshold;
        infiniteWaterThreshold = waterThreshold;
    }
    
    /**
     * 检查是否启用无限岩浆
     */
    public static boolean isInfiniteLavaEnabled() {
        return enableInfiniteLava;
    }
    
    /**
     * 检查是否启用无限水
     */
    public static boolean isInfiniteWaterEnabled() {
        return enableInfiniteWater;
    }
    
    /**
     * 获取无限岩浆阈值
     */
    public static int getInfiniteLavaThreshold() {
        return infiniteLavaThreshold;
    }
    
    /**
     * 获取无限水阈值
     */
    public static int getInfiniteWaterThreshold() {
        return infiniteWaterThreshold;
    }
    
    /**
     * 检查流体是否应该显示为无限
     */
    public static boolean shouldShowInfinite(String fluidType, int units) {
        if (fluidType == null) return false;
        
        switch (fluidType) {
            case "lava":
                return enableInfiniteLava && units >= infiniteLavaThreshold;
            case "water":
                return enableInfiniteWater && units >= infiniteWaterThreshold;
            default:
                return false;
        }
    }
}
