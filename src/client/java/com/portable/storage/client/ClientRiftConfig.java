package com.portable.storage.client;

import net.minecraft.util.Identifier;

/**
 * 客户端裂隙配置状态
 * 存储从服务端同步的裂隙配置信息
 */
public class ClientRiftConfig {
    private static String riftUpgradeItem = "block:minecraft:dragon_egg";
    private static int riftSize = 1;
    
    /**
     * 更新裂隙配置
     */
    public static void updateConfig(String upgradeItem, int size) {
        riftUpgradeItem = upgradeItem;
        riftSize = size;
    }
    
    /**
     * 获取裂隙升级物品
     */
    public static String getRiftUpgradeItem() {
        return riftUpgradeItem;
    }
    
    /**
     * 获取裂隙大小
     */
    public static int getRiftSize() {
        return riftSize;
    }
    
    /**
     * 获取裂隙升级物品的翻译键
     */
    public static String getRiftUpgradeItemTranslationKey() {
        try {
            // 解析格式: "类型:命名空间:物品ID"
            String[] parts = riftUpgradeItem.split(":", 3);
            if (parts.length == 3) {
                String type = parts[0];
                String namespace = parts[1];
                String path = parts[2];
                
                // 直接使用配置的类型前缀
                return type + "." + namespace + "." + path;
            } else {
                // 兼容旧格式: "命名空间:物品ID"
                Identifier id = Identifier.tryParse(riftUpgradeItem);
                if (id != null) {
                    return "item." + id.getNamespace() + "." + id.getPath();
                }
            }
        } catch (Exception ignored) {}
        return "block.minecraft.dragon_egg"; // 默认回退
    }
}
