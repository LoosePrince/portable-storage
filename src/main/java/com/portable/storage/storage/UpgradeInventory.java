package com.portable.storage.storage;

import com.portable.storage.player.PlayerStorageAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 升级槽位库存，使用统一升级管理器
 * 支持5个基础槽位、5个扩展槽位、垃圾桶槽位和流体槽位
 * 基础槽位只能放置特定类型的物品，每个槽位最多1个
 * 扩展槽位仅在箱子升级激活时可用，使用独立序号管理
 * 垃圾桶槽位和流体槽位为特殊槽位，无序号，位置固定
 */
public class UpgradeInventory {
    private static final int BASE_SLOT_COUNT = 5;
    private static final int EXTENDED_SLOT_COUNT = 5;
    private static final int TOTAL_SLOT_COUNT = BASE_SLOT_COUNT + EXTENDED_SLOT_COUNT;
    
    // 使用统一升级管理器
    private final UnifiedUpgradeManager upgradeManager;
    
    
    public UpgradeInventory() {
        this.upgradeManager = new UnifiedUpgradeManager();
    }
    
    /**
     * 获取总槽位数量
     */
    public int getSlotCount() {
        return TOTAL_SLOT_COUNT;
    }
    
    /**
     * 获取基础槽位数量
     */
    public int getBaseSlotCount() {
        return BASE_SLOT_COUNT;
    }
    
    /**
     * 获取扩展槽位数量
     */
    public int getExtendedSlotCount() {
        return EXTENDED_SLOT_COUNT;
    }
    
    /**
     * 获取指定槽位的物品
     * 基础槽位：0-4
     * 扩展槽位：5-9（映射到统一管理器的0-4）
     */
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        
        if (slot < BASE_SLOT_COUNT) {
            // 基础槽位
            return upgradeManager.getBaseSlot(slot);
        } else {
            // 扩展槽位（映射到统一管理器的0-4）
            int extendedSlot = slot - BASE_SLOT_COUNT;
            return upgradeManager.getExtendedSlot(extendedSlot);
        }
    }
    
    /**
     * 获取流体槽位的物品
     */
    public ItemStack getFluidStack() {
        return upgradeManager.getFluidSlot();
    }
    
    /**
     * 设置流体槽位的物品
     */
    public void setFluidStack(ItemStack stack) {
        upgradeManager.setFluidSlot(stack);
    }
    
    /**
     * 获取流体单位数量
     */
    public int getFluidUnits(String fluidType) {
        return upgradeManager.getFluidUnits(fluidType);
    }
    
    /**
     * 设置流体单位数量
     */
    public void setFluidUnits(String fluidType, int units) {
        upgradeManager.setFluidUnits(fluidType, units);
    }
    
    /**
     * 添加流体单位
     */
    public void addFluidUnits(String fluidType, int units) {
        upgradeManager.addFluidUnits(fluidType, units);
    }
    
    /**
     * 减少流体单位
     */
    public boolean removeFluidUnits(String fluidType, int units) {
        return upgradeManager.removeFluidUnits(fluidType, units);
    }
    
    /**
     * 设置指定槽位的物品
     */
    public void setStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return;
        }
        
        if (slot < BASE_SLOT_COUNT) {
            // 基础槽位
            upgradeManager.setBaseSlot(slot, stack);
        } else {
            // 扩展槽位（映射到统一管理器的0-4）
            int extendedSlot = slot - BASE_SLOT_COUNT;
            upgradeManager.setExtendedSlot(extendedSlot, stack);
        }
    }
    
    /**
     * 检查物品是否是指定槽位的有效升级物品
     */
    public static boolean isValidUpgradeForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0) {
            return false;
        }
        
        // 基础槽位（0-4）检查特定物品
        if (slot < BASE_SLOT_COUNT) {
            UpgradeSlotType slotType = UpgradeSlotType.getBaseSlotType(slot);
            return slotType != null && slotType.isValidItem(stack);
        }
        
        // 扩展槽位（5-9）检查特定物品
        if (slot < TOTAL_SLOT_COUNT) {
            int extendedSlot = slot - BASE_SLOT_COUNT;
            UpgradeSlotType slotType = UpgradeSlotType.getExtendedSlotType(extendedSlot);
            return slotType != null && slotType.isValidItem(stack);
        }
        
        return false;
    }
    
    /**
     * 检查物品是否是有效的流体槽位物品
     */
    public static boolean isValidFluidItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        return stack.isOf(Items.LAVA_BUCKET) || 
               stack.isOf(Items.WATER_BUCKET) || 
               stack.isOf(Items.MILK_BUCKET) || 
               stack.isOf(Items.BUCKET);
    }
    
    /**
     * 获取流体桶对应的流体类型
     */
    public static String getFluidType(ItemStack stack) {
        if (stack.isOf(Items.LAVA_BUCKET)) {
            return "lava";
        } else if (stack.isOf(Items.WATER_BUCKET)) {
            return "water";
        } else if (stack.isOf(Items.MILK_BUCKET)) {
            return "milk";
        }
        return null;
    }
    
    /**
     * 根据流体类型创建流体桶
     */
    public static ItemStack createFluidBucket(String fluidType) {
        return switch (fluidType) {
            case "lava" -> new ItemStack(Items.LAVA_BUCKET);
            case "water" -> new ItemStack(Items.WATER_BUCKET);
            case "milk" -> new ItemStack(Items.MILK_BUCKET);
            default -> ItemStack.EMPTY;
        };
    }

    /**
     * 获取指定槽位对应的升级物品（用于UI显示）
     */
    public static ItemStack getExpectedUpgradeForSlot(int slot) {
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        
        // 基础槽位（0-4）返回对应升级物品
        if (slot < BASE_SLOT_COUNT) {
            UpgradeSlotType slotType = UpgradeSlotType.getBaseSlotType(slot);
            return slotType != null ? slotType.getExpectedStack() : ItemStack.EMPTY;
        }
        
        // 扩展槽位（5-9）返回对应升级物品
        if (slot < TOTAL_SLOT_COUNT) {
            int extendedSlot = slot - BASE_SLOT_COUNT;
            UpgradeSlotType slotType = UpgradeSlotType.getExtendedSlotType(extendedSlot);
            return slotType != null ? slotType.getExpectedStack() : ItemStack.EMPTY;
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * 获取流体槽位对应的预期物品（用于UI显示）
     */
    public static ItemStack getExpectedFluidForSlot() {
        return new ItemStack(Items.BUCKET);
    }
    
    /**
     * 检查指定槽位是否被禁用（带玩家参数，考虑仓库类型）
     */
    public boolean isSlotDisabled(int slot, ServerPlayerEntity player) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) return false;
        
        // 检查是否为初级仓库限制的槽位
        if (isPrimaryStorageRestrictedSlot(slot)) {
            // 只有在使用初级仓库时才禁用这些槽位
            PlayerStorageAccess access = (PlayerStorageAccess) player;
            return access.portableStorage$getStorageType() == StorageType.PRIMARY;
        }
        
        if (slot < BASE_SLOT_COUNT) {
            // 基础槽位
            return upgradeManager.isBaseSlotDisabled(slot);
        } else {
            // 扩展槽位（映射到统一管理器的0-4）
            int extendedSlot = slot - BASE_SLOT_COUNT;
            return upgradeManager.isExtendedSlotDisabled(extendedSlot);
        }
    }

    /**
     * 设置指定槽位的禁用状态
     */
    public void setSlotDisabled(int slot, boolean disabled) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) return;
        
        if (slot < BASE_SLOT_COUNT) {
            // 基础槽位
            upgradeManager.setBaseSlotDisabled(slot, disabled);
        } else {
            // 扩展槽位（映射到统一管理器的0-4）
            int extendedSlot = slot - BASE_SLOT_COUNT;
            upgradeManager.setExtendedSlotDisabled(extendedSlot, disabled);
        }
    }

    /**
     * 切换指定槽位的禁用状态
     */
    public void toggleSlotDisabled(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) return;
        
        if (slot < BASE_SLOT_COUNT) {
            // 基础槽位
            upgradeManager.toggleBaseSlotDisabled(slot);
        } else {
            // 扩展槽位（映射到统一管理器的0-4）
            int extendedSlot = slot - BASE_SLOT_COUNT;
            upgradeManager.toggleExtendedSlotDisabled(extendedSlot);
        }
    }
    
    /**
     * 检查是否为扩展槽位
     */
    public static boolean isExtendedSlot(int slot) {
        return slot >= BASE_SLOT_COUNT && slot < TOTAL_SLOT_COUNT;
    }
    
    /**
     * 检查是否为初级仓库限制的槽位
     * 初级仓库限制以下槽位：木桶升级(3)、裂隙升级(4)、光灵箭升级(5)、附魔之瓶升级(8)、活塞(9)
     */
    public static boolean isPrimaryStorageRestrictedSlot(int slot) {
        return slot == 3 || slot == 4 || slot == 5 || slot == 8 || slot == 9;
    }
    
    /**
     * 检查玩家是否使用初级仓库
     */
    public static boolean isPlayerUsingPrimaryStorage(ServerPlayerEntity player) {
        if (player == null) return false;
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        return access.portableStorage$getStorageType() == StorageType.PRIMARY;
    }
    
    /**
     * 检查箱子升级是否激活（槽位2有箱子且未被禁用）
     */
    public boolean isChestUpgradeActive() {
        return upgradeManager.isChestUpgradeActive();
    }
    
    /**
     * 检查光灵箭升级是否激活（扩展槽位0有光灵箭且未被禁用）
     */
    public boolean isSpectralArrowUpgradeActive() {
        return upgradeManager.isSpectralArrowUpgradeActive();
    }
    
    /**
     * 检查床升级是否激活（扩展槽位1有床且未被禁用）
     */
    public boolean isBedUpgradeActive() {
        return upgradeManager.isBedUpgradeActive();
    }

    /**
     * 检查附魔之瓶升级是否激活（扩展槽位2有附魔之瓶且未被禁用）
     */
    public boolean isXpBottleUpgradeActive() {
        return upgradeManager.isXpBottleUpgradeActive();
    }
    
    /**
     * 检查活塞升级是否激活（扩展槽位3有活塞且未被禁用）
     */
    public boolean isPistonUpgradeActive() {
        return upgradeManager.isPistonUpgradeActive();
    }
    
    /**
     * 检查附魔金苹果升级是否激活（扩展槽位4有附魔金苹果且未被禁用）
     */
    public boolean isEnchantedGoldenAppleUpgradeActive() {
        return upgradeManager.isEnchantedGoldenAppleUpgradeActive();
    }
    
    /**
     * 检查垃圾桶槽位是否激活
     */
    public boolean isTrashSlotActive() {
        return upgradeManager.isTrashSlotActive();
    }
    
    /**
     * 获取扩展槽位的有效状态（仅在箱子升级激活时有效）
     */
    public boolean isExtendedSlotEnabled(int slot) {
        if (!isExtendedSlot(slot)) return false;
        int extendedSlot = slot - BASE_SLOT_COUNT;
        return upgradeManager.isExtendedSlotEnabled(extendedSlot);
    }
    
    /**
     * 尝试放入物品到指定槽位
     * @param slot 槽位索引
     * @param stack 要放入的物品
     * @param player 玩家对象（可选，用于检查存储类型和获取玩家信息）
     * @param playerUuid 玩家UUID（可选，当没有玩家对象时使用）
     * @param playerName 玩家名称（可选，当没有玩家对象时使用）
     * @return 成功返回 true，失败返回 false
     */
    public boolean tryInsert(int slot, ItemStack stack, ServerPlayerEntity player, java.util.UUID playerUuid, String playerName) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return false;
        }

        // 检查是否为初级仓库限制的槽位
        if (isPrimaryStorageRestrictedSlot(slot)) {
            if (player != null) {
                // 有玩家对象时，检查存储类型
                PlayerStorageAccess access = (PlayerStorageAccess) player;
                if (access.portableStorage$getStorageType() == StorageType.PRIMARY) {
                    return false; // 初级仓库限制的槽位不允许插入
                }
            } else {
                // 没有玩家对象时，默认禁用这些槽位（向后兼容）
                return false;
            }
        }

        if (slot < BASE_SLOT_COUNT) {
            // 基础槽位
            if (player != null) {
                return upgradeManager.tryInsertBaseSlot(slot, stack, player.getUuid(), player.getName().getString());
            } else {
                return upgradeManager.tryInsertBaseSlot(slot, stack, playerUuid, playerName);
            }
        } else {
            // 扩展槽位（映射到统一管理器的0-4）
            int extendedSlot = slot - BASE_SLOT_COUNT;
            return upgradeManager.tryInsertExtendedSlot(extendedSlot, stack);
        }
    }
    
    
    /**
     * 从指定槽位取出物品
     */
    public ItemStack takeStack(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        
        if (slot < BASE_SLOT_COUNT) {
            // 基础槽位
            return upgradeManager.takeBaseSlot(slot);
        } else {
            // 扩展槽位（映射到统一管理器的0-4）
            int extendedSlot = slot - BASE_SLOT_COUNT;
            return upgradeManager.takeExtendedSlot(extendedSlot);
        }
    }
    
    /**
     * 检查是否有特定升级
     */
    public boolean hasUpgrade(ItemStack upgradeType) {
        // 检查基础槽位
        for (int i = 0; i < BASE_SLOT_COUNT; i++) {
            ItemStack slot = upgradeManager.getBaseSlot(i);
            if (ItemStack.areItemsEqual(slot, upgradeType)) {
                return true;
            }
        }
        
        // 检查扩展槽位
        for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
            ItemStack slot = upgradeManager.getExtendedSlot(i);
            if (ItemStack.areItemsEqual(slot, upgradeType)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 保存到 NBT（使用统一管理器）
     */
    public void writeNbt(NbtCompound nbt) {
        upgradeManager.writeNbt(nbt);
    }

    /**
     * 从 NBT 读取（使用统一管理器）
     */
    public void readNbt(NbtCompound nbt) {
        upgradeManager.readNbt(nbt);
    }
    
    /**
     * 获取所有扩展槽位中的物品（用于箱子升级取消时掉落）
     */
    public java.util.List<ItemStack> getExtendedSlotItems() {
        return upgradeManager.getExtendedSlotItems();
    }
    
    /**
     * 清空所有扩展槽位
     */
    public void clearExtendedSlots() {
        upgradeManager.clearExtendedSlots();
    }

    // ===== 附魔之瓶经验池 API =====
    public long getXpPool() { 
        return upgradeManager.getXpPool(); 
    }

    public long addToXpPool(long amount) {
        return upgradeManager.addToXpPool(amount);
    }

    public long removeFromXpPool(long amount) {
        return upgradeManager.removeFromXpPool(amount);
    }

    // ===== 等级维持状态 API =====
    public boolean isLevelMaintenanceEnabled() { 
        return upgradeManager.isLevelMaintenanceEnabled(); 
    }
    
    public void setLevelMaintenanceEnabled(boolean enabled) { 
        upgradeManager.setLevelMaintenanceEnabled(enabled); 
    }
    
    public void toggleLevelMaintenance() { 
        upgradeManager.toggleLevelMaintenance(); 
    }
    
    // ===== 新增方法：垃圾桶槽位管理 =====
    
    /**
     * 获取垃圾桶槽位物品
     */
    public ItemStack getTrashSlot() {
        return upgradeManager.getTrashSlot();
    }
    
    /**
     * 设置垃圾桶槽位物品
     */
    public void setTrashSlot(ItemStack stack) {
        upgradeManager.setTrashSlot(stack);
    }
    
    /**
     * 检查垃圾桶槽位是否被禁用
     */
    public boolean isTrashSlotDisabled() {
        return upgradeManager.isTrashSlotDisabled();
    }
    
    /**
     * 设置垃圾桶槽位禁用状态
     */
    public void setTrashSlotDisabled(boolean disabled) {
        upgradeManager.setTrashSlotDisabled(disabled);
    }
    
    /**
     * 切换垃圾桶槽位禁用状态
     */
    public void toggleTrashSlotDisabled() {
        upgradeManager.toggleTrashSlotDisabled();
    }
    
    /**
     * 尝试插入物品到垃圾桶槽位
     */
    public boolean tryInsertTrashSlot(ItemStack stack) {
        return upgradeManager.tryInsertTrashSlot(stack);
    }
    
    /**
     * 从垃圾桶槽位取出物品
     */
    public ItemStack takeTrashSlot() {
        return upgradeManager.takeTrashSlot();
    }
}

