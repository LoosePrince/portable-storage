package com.portable.storage.storage;

/**
 * 仓库类型枚举
 * 定义不同的仓库类型及其特性
 */
public enum StorageType {
    /**
     * 初级仓库
     * 使用海洋之心激活，功能与完整仓库基本一致
     * 容量限制：36种物品
     */
    PRIMARY("primary", "portable-storage.storage_type.primary", 36),
    
    /**
     * 完整仓库
     * 使用下界之星激活，拥有完整功能
     * 无容量限制
     */
    FULL("full", "portable-storage.storage_type.full", -1);
    
    private final String key;
    private final String displayName;
    private final int capacityLimit;
    
    StorageType(String key, String displayName, int capacityLimit) {
        this.key = key;
        this.displayName = displayName;
        this.capacityLimit = capacityLimit;
    }
    
    public String getKey() {
        return key;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取翻译键
     */
    public String getTranslationKey() {
        return displayName;
    }
    
    public int getCapacityLimit() {
        return capacityLimit;
    }
    
    /**
     * 检查是否有容量限制
     */
    public boolean hasCapacityLimit() {
        return capacityLimit > 0;
    }
    
    /**
     * 根据键值获取仓库类型
     */
    public static StorageType fromKey(String key) {
        for (StorageType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return FULL; // 默认为完整仓库
    }
}
