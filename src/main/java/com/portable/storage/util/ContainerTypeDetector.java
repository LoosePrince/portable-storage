package com.portable.storage.util;

import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * 容器类型检测工具类
 * 用于识别不同类型的容器界面，以便根据配置决定是否显示仓库
 */
public class ContainerTypeDetector {
    
    /**
     * 根据ScreenHandler获取容器标识符
     * @param handler 屏幕处理器
     * @return 容器标识符，如果无法识别则返回null
     */
    public static String getContainerId(ScreenHandler handler) {
        if (handler == null) return null;
        
        try {
            ScreenHandlerType<?> type = handler.getType();
            if (type == null) return null;
            
            Identifier id = Registries.SCREEN_HANDLER.getId(type);
            if (id == null) return null;
            
            return id.toString();
        } catch (UnsupportedOperationException e) {
            // 某些ScreenHandler（如创意模式背包）不支持getType()方法
            // 返回null表示无法识别容器类型
            return null;
        }
    }
    
    /**
     * 检查是否为支持的容器类型
     * @param containerId 容器标识符
     * @return 是否为支持的容器类型
     */
    public static boolean isSupportedContainer(String containerId) {
        if (containerId == null) return false;
        
        switch (containerId) {
            case "minecraft:stonecutter":
            case "minecraft:cartography_table":
            case "minecraft:smithing":
            case "minecraft:grindstone":
            case "minecraft:loom":
            case "minecraft:furnace":
            case "minecraft:smoker":
            case "minecraft:blast_furnace":
            case "minecraft:anvil":
            case "minecraft:enchantment":
            case "minecraft:brewing_stand":
            case "minecraft:beacon":
            case "minecraft:generic_9x3":
            case "minecraft:generic_9x6":
            case "minecraft:generic_3x3":
            case "minecraft:ender_chest":
            case "minecraft:shulker_box":
            case "minecraft:dispenser":
            case "minecraft:dropper":
            case "minecraft:crafter_3x3":
            case "minecraft:hopper":
            case "minecraft:trapped_chest":
            case "minecraft:hopper_minecart":
            case "minecraft:chest_minecart":
            case "minecraft:oak_chest_boat":
            case "minecraft:birch_chest_boat":
            case "minecraft:spruce_chest_boat":
            case "minecraft:jungle_chest_boat":
            case "minecraft:acacia_chest_boat":
            case "minecraft:dark_oak_chest_boat":
            case "minecraft:mangrove_chest_boat":
            case "minecraft:cherry_chest_boat":
            case "minecraft:bamboo_chest_raft":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 检查是否为工作台相关容器（需要特殊处理）
     * @param containerId 容器标识符
     * @return 是否为工作台相关容器
     */
    public static boolean isCraftingContainer(String containerId) {
        if (containerId == null) return false;
        
        switch (containerId) {
            case "minecraft:crafting":
            case "portable-storage:portable_crafting":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 检查是否为背包界面
     * @param containerId 容器标识符
     * @return 是否为背包界面
     */
    public static boolean isInventoryContainer(String containerId) {
        return "minecraft:inventory".equals(containerId);
    }
}
