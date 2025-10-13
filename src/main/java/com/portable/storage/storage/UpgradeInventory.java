package com.portable.storage.storage;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;

/**
 * 升级槽位库存，支持5个基础槽位和6个扩展槽位
 * 基础槽位只能放置特定类型的物品，每个槽位最多1个
 * 扩展槽位（6-11）仅在箱子升级激活时可用，使用屏障图标，暂未规划具体功能
 */
public class UpgradeInventory {
    private static final int BASE_SLOT_COUNT = 5;
    private static final int EXTENDED_SLOT_COUNT = 6;
    private static final int TOTAL_SLOT_COUNT = BASE_SLOT_COUNT + EXTENDED_SLOT_COUNT;
    private final ItemStack[] slots;
    private final boolean[] disabledSlots; // 每个槽位的禁用状态
    // 附魔之瓶独立经验池（以经验点记录）
    private long xpPool = 0L;
    // 等级维持状态（是否启用智能等级维持）
    private boolean levelMaintenanceEnabled = false;
    
    // 每个基础槽位对应的升级物品
    private static final ItemStack[] SLOT_UPGRADES = {
        new ItemStack(Items.CRAFTING_TABLE), // 槽位0：工作台
        new ItemStack(Items.HOPPER),         // 槽位1：漏斗
        new ItemStack(Items.CHEST),          // 槽位2：箱子
        new ItemStack(Items.BARREL),         // 槽位3：木桶
        new ItemStack(Items.SHULKER_BOX)     // 槽位4：潜影盒
    };
    
    // 扩展槽位使用屏障图标
    private static final ItemStack EXTENDED_SLOT_ICON = new ItemStack(Items.BARRIER);
    
    // 流体槽位物品
    private ItemStack fluidStack = ItemStack.EMPTY;
    
    // 流体单位存储
    private int lavaUnits = 0;
    private int waterUnits = 0;
    private int milkUnits = 0;
    
    public UpgradeInventory() {
        this.slots = new ItemStack[TOTAL_SLOT_COUNT];
        this.disabledSlots = new boolean[TOTAL_SLOT_COUNT];
        for (int i = 0; i < TOTAL_SLOT_COUNT; i++) {
            this.slots[i] = ItemStack.EMPTY;
            this.disabledSlots[i] = false;
        }
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
     */
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return slots[slot];
    }
    
    /**
     * 获取流体槽位的物品
     */
    public ItemStack getFluidStack() {
        return fluidStack;
    }
    
    /**
     * 设置流体槽位的物品
     */
    public void setFluidStack(ItemStack stack) {
        this.fluidStack = stack;
    }
    
    /**
     * 获取流体单位数量
     */
    public int getFluidUnits(String fluidType) {
        return switch (fluidType) {
            case "lava" -> lavaUnits;
            case "water" -> waterUnits;
            case "milk" -> milkUnits;
            default -> 0;
        };
    }
    
    /**
     * 设置流体单位数量
     */
    public void setFluidUnits(String fluidType, int units) {
        switch (fluidType) {
            case "lava" -> this.lavaUnits = Math.max(0, units);
            case "water" -> this.waterUnits = Math.max(0, units);
            case "milk" -> this.milkUnits = Math.max(0, units);
        }
    }
    
    /**
     * 添加流体单位
     */
    public void addFluidUnits(String fluidType, int units) {
        setFluidUnits(fluidType, getFluidUnits(fluidType) + units);
    }
    
    /**
     * 减少流体单位
     */
    public boolean removeFluidUnits(String fluidType, int units) {
        int current = getFluidUnits(fluidType);
        if (current >= units) {
            setFluidUnits(fluidType, current - units);
            return true;
        }
        return false;
    }
    
    /**
     * 设置指定槽位的物品
     */
    public void setStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return;
        }
        this.slots[slot] = stack;
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
            return ItemStack.areItemsEqual(stack, SLOT_UPGRADES[slot]);
        }
        
        // 扩展槽位（5-10）检查特定物品
        if (slot < TOTAL_SLOT_COUNT) {
            switch (slot) {
                case 5: // 光灵箭升级
                    return stack.isOf(Items.SPECTRAL_ARROW);
                case 6: // 床升级
                    return stack.isOf(Items.RED_BED) || stack.isOf(Items.BLACK_BED) || 
                           stack.isOf(Items.BLUE_BED) || stack.isOf(Items.BROWN_BED) || 
                           stack.isOf(Items.CYAN_BED) || stack.isOf(Items.GRAY_BED) || 
                           stack.isOf(Items.GREEN_BED) || stack.isOf(Items.LIGHT_BLUE_BED) || 
                           stack.isOf(Items.LIGHT_GRAY_BED) || stack.isOf(Items.LIME_BED) || 
                           stack.isOf(Items.MAGENTA_BED) || stack.isOf(Items.ORANGE_BED) || 
                           stack.isOf(Items.PINK_BED) || stack.isOf(Items.PURPLE_BED) || 
                           stack.isOf(Items.WHITE_BED) || stack.isOf(Items.YELLOW_BED);
                case 7: // 附魔之瓶（经验）升级
                    return stack.isOf(Items.EXPERIENCE_BOTTLE);
                case 8: case 9:
                    // 其他扩展槽位暂时不接受任何物品
                    return false;
                case 10: // 垃圾桶槽位
                    // 垃圾桶槽位接受任何物品
                    return true;
                default:
                    return false;
            }
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
            return SLOT_UPGRADES[slot].copy();
        }
        
        // 扩展槽位（5-10）返回对应升级物品或屏障图标
        if (slot < TOTAL_SLOT_COUNT) {
            switch (slot) {
                case 5: // 光灵箭升级
                    return new ItemStack(Items.SPECTRAL_ARROW);
                case 6: // 床升级
                    return new ItemStack(Items.RED_BED);
                case 7: // 附魔之瓶升级
                    return new ItemStack(Items.EXPERIENCE_BOTTLE);
                case 8: case 9:
                    // 其他扩展槽位返回屏障图标
                    return EXTENDED_SLOT_ICON.copy();
                case 10: // 垃圾桶槽位
                    return new ItemStack(Items.CAULDRON); // 使用炼药锅作为垃圾桶图标
                default:
                    return EXTENDED_SLOT_ICON.copy();
            }
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
     * 检查指定槽位是否被禁用
     */
    public boolean isSlotDisabled(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) return false;
        return disabledSlots[slot];
    }

    /**
     * 设置指定槽位的禁用状态
     */
    public void setSlotDisabled(int slot, boolean disabled) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) return;
        disabledSlots[slot] = disabled;
    }

    /**
     * 切换指定槽位的禁用状态
     */
    public void toggleSlotDisabled(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) return;
        disabledSlots[slot] = !disabledSlots[slot];
    }
    
    /**
     * 检查是否为扩展槽位
     */
    public static boolean isExtendedSlot(int slot) {
        return slot >= BASE_SLOT_COUNT && slot < TOTAL_SLOT_COUNT;
    }
    
    /**
     * 检查箱子升级是否激活（槽位2有箱子且未被禁用）
     */
    public boolean isChestUpgradeActive() {
        ItemStack chestStack = getStack(2); // 槽位2是箱子
        return !chestStack.isEmpty() && !isSlotDisabled(2);
    }
    
    /**
     * 检查光灵箭升级是否激活（槽位5有光灵箭且未被禁用）
     */
    public boolean isSpectralArrowUpgradeActive() {
        ItemStack spectralArrowStack = getStack(5); // 槽位5是光灵箭
        return !spectralArrowStack.isEmpty() && !isSlotDisabled(5);
    }
    
    /**
     * 检查床升级是否激活（槽位6有床且未被禁用）
     */
    public boolean isBedUpgradeActive() {
        ItemStack bedStack = getStack(6); // 槽位6是床
        return !bedStack.isEmpty() && !isSlotDisabled(6);
    }

    public boolean isXpBottleUpgradeActive() {
        ItemStack xpStack = getStack(7); // 槽位7是附魔之瓶
        return !xpStack.isEmpty() && !isSlotDisabled(7);
    }
    
    /**
     * 检查垃圾桶槽位是否激活（槽位10有物品且未被禁用）
     */
    public boolean isTrashSlotActive() {
        ItemStack trashStack = getStack(10); // 槽位10是垃圾桶
        return !trashStack.isEmpty() && !isSlotDisabled(10);
    }
    
    /**
     * 获取扩展槽位的有效状态（仅在箱子升级激活时有效）
     */
    public boolean isExtendedSlotEnabled(int slot) {
        if (!isExtendedSlot(slot)) return false;
        return isChestUpgradeActive() && !isSlotDisabled(slot);
    }
    
    /**
     * 尝试放入物品到指定槽位
     * @return 成功返回 true，失败返回 false
     */
    public boolean tryInsert(int slot, ItemStack stack) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return false;
        }

        // 垃圾桶槽位特殊处理
        if (slot == 10) {
            // 垃圾桶槽位：接受任何物品，最大一组，覆盖机制
            int maxCount = Math.min(stack.getCount(), stack.getMaxCount());
            slots[slot] = stack.copy();
            slots[slot].setCount(maxCount);
            return true;
        }

        // 扩展槽位检查特定物品
        if (isExtendedSlot(slot)) {
            // 只有槽位5（光灵箭）、槽位6（床）、槽位7（附魔之瓶）可以接受物品
            if (slot != 5 && slot != 6 && slot != 7) {
                return false;
            }
        }

        // 检查物品是否是该槽位的有效升级物品
        if (!isValidUpgradeForSlot(slot, stack)) {
            return false;
        }

        // 检查槽位是否为空
        if (!slots[slot].isEmpty()) {
            return false;
        }

        // 检查数量是否为1
        if (stack.getCount() != 1) {
            return false;
        }

        // 放入物品
        slots[slot] = stack.copy();
        return true;
    }
    
    /**
     * 从指定槽位取出物品
     */
    public ItemStack takeStack(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        
        ItemStack result = slots[slot];
        slots[slot] = ItemStack.EMPTY;
        return result;
    }
    
    /**
     * 检查是否有特定升级
     */
    public boolean hasUpgrade(ItemStack upgradeType) {
        for (ItemStack slot : slots) {
            if (ItemStack.areItemsEqual(slot, upgradeType)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 保存到 NBT（包含自定义数据，确保如木桶绑定等信息不会丢失）
     */
    public void writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (int i = 0; i < TOTAL_SLOT_COUNT; i++) {
            if (!slots[i].isEmpty()) {
                NbtCompound slotNbt = new NbtCompound();
                slotNbt.putByte("Slot", (byte) i);
                slotNbt.putString("id", Registries.ITEM.getId(slots[i].getItem()).toString());
                slotNbt.putByte("Count", (byte) slots[i].getCount());
                // 写入自定义数据（如绑定拥有者）
                NbtComponent custom = slots[i].get(DataComponentTypes.CUSTOM_DATA);
                if (custom != null) {
                    slotNbt.put("CustomData", custom.copyNbt());
                }
                // 写入方块实体数据（用于方块放置/破坏保持）
                NbtComponent be = slots[i].get(DataComponentTypes.BLOCK_ENTITY_DATA);
                if (be != null) {
                    slotNbt.put("BlockEntityData", be.copyNbt());
                }
                list.add(slotNbt);
            }
        }
        nbt.put("Upgrades", list);

        // 保存禁用状态
        NbtCompound disabledNbt = new NbtCompound();
        for (int i = 0; i < TOTAL_SLOT_COUNT; i++) {
            disabledNbt.putBoolean("slot" + i, disabledSlots[i]);
        }
        nbt.put("DisabledSlots", disabledNbt);
        
        // 保存流体槽位
        if (!fluidStack.isEmpty()) {
            NbtCompound fluidNbt = new NbtCompound();
            fluidNbt.putString("id", Registries.ITEM.getId(fluidStack.getItem()).toString());
            fluidNbt.putByte("Count", (byte) fluidStack.getCount());
            // 写入自定义数据
            NbtComponent custom = fluidStack.get(DataComponentTypes.CUSTOM_DATA);
            if (custom != null) {
                fluidNbt.put("CustomData", custom.copyNbt());
            }
            nbt.put("FluidStack", fluidNbt);
        }
        
        // 保存流体单位
        nbt.putInt("LavaUnits", lavaUnits);
        nbt.putInt("WaterUnits", waterUnits);
        nbt.putInt("MilkUnits", milkUnits);
        
        // 写入附魔之瓶经验池
        nbt.putLong("XpPool", Math.max(0L, xpPool));
        // 写入等级维持状态
        nbt.putBoolean("LevelMaintenanceEnabled", levelMaintenanceEnabled);
    }

    /**
     * 从 NBT 读取（包含自定义数据）
     */
    public void readNbt(NbtCompound nbt) {
        for (int i = 0; i < TOTAL_SLOT_COUNT; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        NbtList list = nbt.getList("Upgrades", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound slotNbt = list.getCompound(i);
            int slot = slotNbt.getByte("Slot") & 255;
            if (slot >= 0 && slot < TOTAL_SLOT_COUNT && slotNbt.contains("id")) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(slotNbt.getString("id"));
                if (id != null) {
                    net.minecraft.item.Item item = Registries.ITEM.get(id);
                    if (item != null && item != Items.AIR) {
                        ItemStack stack = new ItemStack(item);
                        stack.setCount(slotNbt.getByte("Count"));
                        if (slotNbt.contains("CustomData", NbtElement.COMPOUND_TYPE)) {
                            NbtCompound custom = slotNbt.getCompound("CustomData");
                            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
                        }
                        if (slotNbt.contains("BlockEntityData", NbtElement.COMPOUND_TYPE)) {
                            NbtCompound be = slotNbt.getCompound("BlockEntityData");
                            stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(be));
                        }
                        slots[slot] = stack;
                    }
                }
            }
        }

        // 加载禁用状态
        if (nbt.contains("DisabledSlots", NbtElement.COMPOUND_TYPE)) {
            NbtCompound disabledNbt = nbt.getCompound("DisabledSlots");
            for (int i = 0; i < TOTAL_SLOT_COUNT; i++) {
                disabledSlots[i] = disabledNbt.getBoolean("slot" + i);
            }
        } else {
            // 如果没有禁用状态数据，初始化为false
            for (int i = 0; i < TOTAL_SLOT_COUNT; i++) {
                disabledSlots[i] = false;
            }
        }
        // 读取流体槽位
        if (nbt.contains("FluidStack", NbtElement.COMPOUND_TYPE)) {
            NbtCompound fluidNbt = nbt.getCompound("FluidStack");
            if (fluidNbt.contains("id")) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(fluidNbt.getString("id"));
                if (id != null) {
                    net.minecraft.item.Item item = Registries.ITEM.get(id);
                    if (item != null && item != Items.AIR) {
                        ItemStack stack = new ItemStack(item);
                        stack.setCount(fluidNbt.getByte("Count"));
                        if (fluidNbt.contains("CustomData", NbtElement.COMPOUND_TYPE)) {
                            NbtCompound custom = fluidNbt.getCompound("CustomData");
                            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
                        }
                        fluidStack = stack;
                    }
                }
            }
        } else {
            fluidStack = ItemStack.EMPTY;
        }
        
        // 读取流体单位
        lavaUnits = nbt.getInt("LavaUnits");
        waterUnits = nbt.getInt("WaterUnits");
        milkUnits = nbt.getInt("MilkUnits");
        
        // 读取附魔之瓶经验池
        if (nbt.contains("XpPool", NbtElement.LONG_TYPE)) {
            xpPool = Math.max(0L, nbt.getLong("XpPool"));
        } else {
            xpPool = 0L;
        }
        // 读取等级维持状态
        if (nbt.contains("LevelMaintenanceEnabled", 1)) { // 1 = BOOLEAN_TYPE
            levelMaintenanceEnabled = nbt.getBoolean("LevelMaintenanceEnabled");
        } else {
            levelMaintenanceEnabled = false;
        }
    }
    
    /**
     * 获取所有扩展槽位中的物品（用于箱子升级取消时掉落）
     */
    public java.util.List<ItemStack> getExtendedSlotItems() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = BASE_SLOT_COUNT; i < TOTAL_SLOT_COUNT; i++) {
            if (!slots[i].isEmpty()) {
                items.add(slots[i].copy());
            }
        }
        return items;
    }
    
    /**
     * 清空所有扩展槽位
     */
    public void clearExtendedSlots() {
        for (int i = BASE_SLOT_COUNT; i < TOTAL_SLOT_COUNT; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    // ===== 附魔之瓶经验池 API =====
    public long getXpPool() { return Math.max(0L, xpPool); }

    public long addToXpPool(long amount) {
        if (amount <= 0) return 0L;
        long before = xpPool;
        long after;
        try {
            after = Math.addExact(before, amount);
        } catch (ArithmeticException ex) {
            after = Long.MAX_VALUE;
        }
        xpPool = after;
        return after - before;
    }

    public long removeFromXpPool(long amount) {
        if (amount <= 0) return 0L;
        long removed = Math.min(amount, Math.max(0L, xpPool));
        xpPool -= removed;
        if (xpPool < 0L) xpPool = 0L;
        return removed;
    }

    // ===== 等级维持状态 API =====
    public boolean isLevelMaintenanceEnabled() { return levelMaintenanceEnabled; }
    
    public void setLevelMaintenanceEnabled(boolean enabled) { 
        levelMaintenanceEnabled = enabled; 
    }
    
    public void toggleLevelMaintenance() { 
        levelMaintenanceEnabled = !levelMaintenanceEnabled; 
    }
}

