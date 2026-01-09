package com.portablestorage.util;

import net.minecraft.util.StringRepresentable;

/**
 * 仓库设置枚举
 * 定义仓库的各种设置选项
 */
public enum WarehouseSetting implements StringRepresentable {
    /** 折叠/展开 */
    FOLD(0),
    /** 排序模式 */
    SORT_MODE(1),
    /** 排序顺序 */
    SORT_ORDER(2),
    /** 快速交互 */
    QUICK_INTERACTION(3),
    /** 智能折叠 */
    SMART_COLLAPSE(4),
    /** 合成补充 */
    CRAFT_REFILL(5);

    /** 设置 ID */
    private final int id;

    WarehouseSetting(int id) {
        this.id = id;
    }

    /**
     * 获取设置 ID
     * @return 设置 ID
     */
    public int getId() {
        return id;
    }

    /**
     * 根据 ID 获取设置
     * @param id 设置 ID
     * @return 设置枚举值，如果无效则返回 FOLD
     */
    public static WarehouseSetting fromId(int id) {
        for (WarehouseSetting setting : values()) {
            if (setting.id == id) return setting;
        }
        return FOLD;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}

