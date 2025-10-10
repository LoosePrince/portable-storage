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
        // XP 池（客户端仅用于显示）
        if (nbt.contains("XpPool")) {
            cachedXpPool = Math.max(0L, nbt.getLong("XpPool"));
        }
        // 等级维持状态
        if (nbt.contains("LevelMaintenanceEnabled")) {
            cachedLevelMaintenanceEnabled = nbt.getBoolean("LevelMaintenanceEnabled");
        }
        
        // 读取禁用状态
        if (nbt.contains("DisabledSlots")) {
            NbtCompound disabledNbt = nbt.getCompound("DisabledSlots");
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
    
    /**
     * 检查床升级是否激活
     */
    public static boolean isBedUpgradeActive() {
        return upgradeInventory.isBedUpgradeActive();
    }

    /**
     * 检查附魔之瓶（经验）升级是否激活（槽位7）
     */
    public static boolean isXpBottleUpgradeActive() {
        net.minecraft.item.ItemStack stack = upgradeInventory.getStack(7);
        return stack != null && !stack.isEmpty() && !upgradeInventory.isSlotDisabled(7);
    }

    // ===== XP 池缓存（只用来显示） =====
    private static long cachedXpPool = 0L;
    public static long getCachedXpPool() { return Math.max(0L, cachedXpPool); }
    
    // ===== 等级维持状态缓存 =====
    private static boolean cachedLevelMaintenanceEnabled = false;
    public static boolean isLevelMaintenanceEnabled() { return cachedLevelMaintenanceEnabled; }
    
    // ===== XP 传输步长管理 =====
    private static int xpTransferStep = 0; // 0=1级, 1=5级, 2=10级, 3=100级
    public static int getXpTransferStep() { return xpTransferStep; }
    
    public static void cycleXpTransferStep() {
        xpTransferStep = (xpTransferStep + 1) % 4; // 循环 0,1,2,3
    }
    
    public static void setXpTransferStep(int step) {
        xpTransferStep = Math.max(0, Math.min(3, step)); // 确保在 0-3 范围内
    }
}

