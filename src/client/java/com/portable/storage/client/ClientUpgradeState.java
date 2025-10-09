package com.portable.storage.client;

import com.portable.storage.storage.UpgradeInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * 客户端缓存的升级槽位状态
 */
public final class ClientUpgradeState {
    private static final UpgradeInventory upgradeInventory = new UpgradeInventory();
    private static final boolean[] disabledSlots = new boolean[11]; // 11个槽位的禁用状态（5个基础+6个扩展）

    private ClientUpgradeState() {}

    public static void updateFromNbt(NbtCompound nbt) {
        upgradeInventory.readNbt(nbt);
        
        // 读取禁用状态
        if (nbt.contains("disabledSlots")) {
            NbtCompound disabledNbt = nbt.getCompound("disabledSlots");
            for (int i = 0; i < 11; i++) {
                disabledSlots[i] = disabledNbt.getBoolean("slot" + i);
            }
        }
    }

    public static ItemStack getStack(int slot) {
        return upgradeInventory.getStack(slot);
    }

    public static int getSlotCount() {
        return upgradeInventory.getSlotCount();
    }
    
    /**
     * 检查指定槽位是否被禁用
     */
    public static boolean isSlotDisabled(int slot) {
        if (slot < 0 || slot >= 11) return false;
        return disabledSlots[slot];
    }
    
    /**
     * 切换指定槽位的禁用状态
     */
    public static void toggleSlotDisabled(int slot) {
        if (slot < 0 || slot >= 11) return;
        disabledSlots[slot] = !disabledSlots[slot];
    }
    
    /**
     * 设置指定槽位的禁用状态
     */
    public static void setSlotDisabled(int slot, boolean disabled) {
        if (slot < 0 || slot >= 11) return;
        disabledSlots[slot] = disabled;
    }
    
    /**
     * 检查是否为扩展槽位
     */
    public static boolean isExtendedSlot(int slot) {
        return com.portable.storage.storage.UpgradeInventory.isExtendedSlot(slot);
    }
    
    /**
     * 检查箱子升级是否激活
     */
    public static boolean isChestUpgradeActive() {
        return upgradeInventory.isChestUpgradeActive();
    }
    
    /**
     * 获取扩展槽位的有效状态（仅在箱子升级激活时有效）
     */
    public static boolean isExtendedSlotEnabled(int slot) {
        return upgradeInventory.isExtendedSlotEnabled(slot);
    }
    
    /**
     * 检查光灵箭升级是否激活
     */
    public static boolean isSpectralArrowUpgradeActive() {
        return upgradeInventory.isSpectralArrowUpgradeActive();
    }
}

