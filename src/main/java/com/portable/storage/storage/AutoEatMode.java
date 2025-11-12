package com.portable.storage.storage;

/**
 * 自动进食模式枚举
 * 定义附魔金苹果升级的自动喂食数
 * 0=禁用, 2,4,6,8,10,12,14,16,18,20=不同的喂食数
 */
public enum AutoEatMode {
    /**
     * 禁用：不自动喂食
     */
    DISABLED(0, 0),
    /**
     * 启用
     */
    COUNT_2(1, 2),
    COUNT_4(2, 4),
    COUNT_6(3, 6),
    COUNT_8(4, 8),
    COUNT_10(5, 10),
    COUNT_12(6, 12),
    COUNT_14(7, 14),
    COUNT_16(8, 16),
    COUNT_18(9, 18),
    COUNT_20(10, 20);
    
    private final int index;
    private final int feedCount;
    
    AutoEatMode(int index, int feedCount) {
        this.index = index;
        this.feedCount = feedCount;
    }
    
    /**
     * 获取模式索引
     */
    public int getIndex() {
        return index;
    }
    
    /**
     * 获取喂食数（0表示禁用）
     */
    public int getFeedCount() {
        return feedCount;
    }
    
    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return feedCount > 0;
    }
    
    /**
     * 根据索引获取模式
     */
    public static AutoEatMode fromIndex(int index) {
        for (AutoEatMode mode : values()) {
            if (mode.index == index) {
                return mode;
            }
        }
        return DISABLED; // 默认返回禁用
    }
    
    /**
     * 根据喂食数获取模式
     */
    public static AutoEatMode fromFeedCount(int feedCount) {
        for (AutoEatMode mode : values()) {
            if (mode.feedCount == feedCount) {
                return mode;
            }
        }
        return DISABLED;
    }
    
    /**
     * 获取下一个模式（循环）：禁用->2->4->6->...->18->20->禁用
     */
    public AutoEatMode next() {
        int nextIndex = (this.index + 1) % values().length;
        return fromIndex(nextIndex);
    }
}
