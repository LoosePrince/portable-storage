package com.portablestorage.upgrade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * 升级注册表
 * 管理所有已注册的升级类型
 */
public class UpgradeRegistry {
    /** 升级类型映射表 */
    private static final Map<Identifier, UpgradeType> UPGRADES = new LinkedHashMap<>();
    /** 排序后的升级 ID 列表 */
    private static final List<Identifier> SORTED_IDS = new ArrayList<>();

    /**
     * 注册升级类型
     * 
     * @param upgrade 升级类型实例
     */
    public static void register(UpgradeType upgrade) {
        UPGRADES.put(upgrade.getId(), upgrade);
        if (!SORTED_IDS.contains(upgrade.getId())) {
            SORTED_IDS.add(upgrade.getId());
        }
    }

    /**
     * 根据 ID 获取升级类型
     * 
     * @param id 升级 ID
     * @return 升级类型，如果不存在则返回 null
     */
    public static UpgradeType get(Identifier id) {
        return UPGRADES.get(id);
    }

    /**
     * 获取所有已注册的升级类型
     * 
     * @return 升级类型列表
     */
    public static List<UpgradeType> getAllUpgrades() {
        return new ArrayList<>(UPGRADES.values());
    }

    /**
     * 获取已注册的升级数量
     * 
     * @return 升级数量
     */
    public static int getUpgradeCount() {
        return UPGRADES.size();
    }

    /**
     * 根据物品堆叠获取对应的升级类型
     * 
     * @param stack 物品堆叠
     * @return 升级类型，如果不匹配则返回 null
     */
    public static UpgradeType getByItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty())
            return null;
        for (UpgradeType type : UPGRADES.values()) {
            if (type.isItemValid(stack))
                return type;
        }
        return null;
    }
}
