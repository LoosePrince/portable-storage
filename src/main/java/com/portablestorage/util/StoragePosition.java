package com.portablestorage.util;

/**
 * 仓库显示位置枚举
 * 定义仓库在背包界面中的显示位置
 */
public enum StoragePosition {
    /** 顶部 */
    TOP,
    /** 底部 */
    BOTTOM,
    /** 左侧 */
    LEFT,
    /** 右侧 */
    RIGHT;

    /**
     * 是否为垂直位置
     * @return 顶部或底部返回 true
     */
    public boolean isVertical() {
        return this == TOP || this == BOTTOM;
    }

    /**
     * 是否为水平位置
     * @return 左侧或右侧返回 true
     */
    public boolean isHorizontal() {
        return this == LEFT || this == RIGHT;
    }
}

