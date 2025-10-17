package com.portable.storage.storage;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 升级槽位类型枚举
 * 统一管理所有升级槽位的类型、物品和功能
 */
public enum UpgradeSlotType {
    // 基础升级槽位（0-4）
    CRAFTING_TABLE(0, Items.CRAFTING_TABLE, "crafting_table"),
    HOPPER(1, Items.HOPPER, "hopper"),
    CHEST(2, Items.CHEST, "chest"),
    BARREL(3, Items.BARREL, "barrel"),
    DRAGON_EGG(4, Items.DRAGON_EGG, "dragon_egg"),
    
    // 扩展升级槽位（独立序号0-4）
    SPECTRAL_ARROW(0, Items.SPECTRAL_ARROW, "spectral_arrow", true),
    BED(1, Items.RED_BED, "bed", true),
    EXPERIENCE_BOTTLE(2, Items.EXPERIENCE_BOTTLE, "experience_bottle", true),
    PISTON(3, Items.PISTON, "piston", true),
    RESERVED(4, Items.BARRIER, "reserved", true);
    
    private final int slotIndex;
    private final net.minecraft.item.Item expectedItem;
    private final String translationKey;
    private final boolean isExtended;
    
    UpgradeSlotType(int slotIndex, net.minecraft.item.Item expectedItem, String translationKey) {
        this(slotIndex, expectedItem, translationKey, false);
    }
    
    UpgradeSlotType(int slotIndex, net.minecraft.item.Item expectedItem, String translationKey, boolean isExtended) {
        this.slotIndex = slotIndex;
        this.expectedItem = expectedItem;
        this.translationKey = translationKey;
        this.isExtended = isExtended;
    }
    
    /**
     * 获取槽位索引
     */
    public int getSlotIndex() {
        return slotIndex;
    }
    
    /**
     * 获取预期物品
     */
    public net.minecraft.item.Item getExpectedItem() {
        return expectedItem;
    }
    
    /**
     * 获取翻译键
     */
    public String getTranslationKey() {
        return translationKey;
    }
    
    /**
     * 是否为扩展槽位
     */
    public boolean isExtended() {
        return isExtended;
    }
    
    /**
     * 获取预期物品堆栈
     */
    public ItemStack getExpectedStack() {
        return new ItemStack(expectedItem);
    }
    
    /**
     * 根据基础槽位索引获取槽位类型
     */
    public static UpgradeSlotType getBaseSlotType(int slotIndex) {
        for (UpgradeSlotType type : values()) {
            if (!type.isExtended && type.slotIndex == slotIndex) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * 根据扩展槽位索引获取槽位类型
     */
    public static UpgradeSlotType getExtendedSlotType(int slotIndex) {
        for (UpgradeSlotType type : values()) {
            if (type.isExtended && type.slotIndex == slotIndex) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * 检查物品是否匹配此槽位类型
     */
    public boolean isValidItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        // 床升级支持所有颜色的床
        if (this == BED) {
            return stack.isOf(Items.RED_BED) || stack.isOf(Items.BLACK_BED) || 
                   stack.isOf(Items.BLUE_BED) || stack.isOf(Items.BROWN_BED) || 
                   stack.isOf(Items.CYAN_BED) || stack.isOf(Items.GRAY_BED) || 
                   stack.isOf(Items.GREEN_BED) || stack.isOf(Items.LIGHT_BLUE_BED) || 
                   stack.isOf(Items.LIGHT_GRAY_BED) || stack.isOf(Items.LIME_BED) || 
                   stack.isOf(Items.MAGENTA_BED) || stack.isOf(Items.ORANGE_BED) || 
                   stack.isOf(Items.PINK_BED) || stack.isOf(Items.PURPLE_BED) || 
                   stack.isOf(Items.WHITE_BED) || stack.isOf(Items.YELLOW_BED);
        }
        
        return stack.isOf(expectedItem);
    }
}
