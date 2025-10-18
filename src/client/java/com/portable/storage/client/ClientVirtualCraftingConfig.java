package com.portable.storage.client;

/**
 * 客户端虚拟合成配置状态
 * 存储从服务端同步的虚拟合成配置信息
 */
public class ClientVirtualCraftingConfig {
    private static boolean enableVirtualCrafting = true;
    
    /**
     * 更新虚拟合成配置
     */
    public static void updateConfig(boolean enabled) {
        enableVirtualCrafting = enabled;
    }
    
    /**
     * 获取虚拟合成是否启用
     */
    public static boolean isEnableVirtualCrafting() {
        return enableVirtualCrafting;
    }
}
