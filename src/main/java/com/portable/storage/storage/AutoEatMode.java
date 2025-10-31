package com.portable.storage.storage;

/**
 * 自动进食模式枚举
 * 定义附魔金苹果升级的不同自动进食模式
 */
public enum AutoEatMode {
    /**
     * 兜底模式：低于6点自动吃
     */
    FALLBACK(0, 6, "fallback"),
    
    /**
     * 回血模式：保持至少18点饱食度
     */
    HEAL(1, 18, "heal"),
    
    /**
     * 默认模式：低于14点自动吃
     */
    DEFAULT(2, 14, "default");
    
    private final int index;
    private final int threshold;
    private final String key;
    
    AutoEatMode(int index, int threshold, String key) {
        this.index = index;
        this.threshold = threshold;
        this.key = key;
    }
    
    /**
     * 获取模式索引
     */
    public int getIndex() {
        return index;
    }
    
    /**
     * 获取饱食度阈值
     */
    public int getThreshold() {
        return threshold;
    }
    
    /**
     * 获取翻译键
     */
    public String getKey() {
        return key;
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
        return DEFAULT; // 默认返回默认模式
    }
    
    /**
     * 获取下一个模式（循环）
     */
    public AutoEatMode next() {
        int nextIndex = (this.index + 1) % values().length;
        return fromIndex(nextIndex);
    }
}
