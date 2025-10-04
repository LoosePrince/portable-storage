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
 * 升级槽位库存，支持5个槽位
 * 每个槽位只能放置特定类型的物品，每个槽位最多1个
 */
public class UpgradeInventory {
    private static final int SLOT_COUNT = 5;
    private final ItemStack[] slots;
    private final boolean[] disabledSlots; // 每个槽位的禁用状态
    
    // 每个槽位对应的升级物品
    private static final ItemStack[] SLOT_UPGRADES = {
        new ItemStack(Items.CRAFTING_TABLE), // 槽位0：工作台
        new ItemStack(Items.HOPPER),         // 槽位1：漏斗
        new ItemStack(Items.CHEST),          // 槽位2：箱子
        new ItemStack(Items.BARREL),         // 槽位3：木桶
        new ItemStack(Items.SHULKER_BOX)     // 槽位4：潜影盒
    };
    
    public UpgradeInventory() {
        this.slots = new ItemStack[SLOT_COUNT];
        this.disabledSlots = new boolean[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            this.slots[i] = ItemStack.EMPTY;
            this.disabledSlots[i] = false;
        }
    }
    
    /**
     * 获取槽位数量
     */
    public int getSlotCount() {
        return SLOT_COUNT;
    }
    
    /**
     * 获取指定槽位的物品
     */
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return slots[slot];
    }
    
    /**
     * 设置指定槽位的物品
     */
    public void setStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        this.slots[slot] = stack;
    }
    
    /**
     * 检查物品是否是指定槽位的有效升级物品
     */
    public static boolean isValidUpgradeForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0 || slot >= SLOT_UPGRADES.length) {
            return false;
        }
        return ItemStack.areItemsEqual(stack, SLOT_UPGRADES[slot]);
    }

    /**
     * 获取指定槽位对应的升级物品（用于UI显示）
     */
    public static ItemStack getExpectedUpgradeForSlot(int slot) {
        if (slot < 0 || slot >= SLOT_UPGRADES.length) {
            return ItemStack.EMPTY;
        }
        return SLOT_UPGRADES[slot].copy();
    }

    /**
     * 检查指定槽位是否被禁用
     */
    public boolean isSlotDisabled(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;
        return disabledSlots[slot];
    }

    /**
     * 设置指定槽位的禁用状态
     */
    public void setSlotDisabled(int slot, boolean disabled) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        disabledSlots[slot] = disabled;
    }

    /**
     * 切换指定槽位的禁用状态
     */
    public void toggleSlotDisabled(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        disabledSlots[slot] = !disabledSlots[slot];
    }
    
    /**
     * 尝试放入物品到指定槽位
     * @return 成功返回 true，失败返回 false
     */
    public boolean tryInsert(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
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
        if (slot < 0 || slot >= SLOT_COUNT) {
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
        for (int i = 0; i < SLOT_COUNT; i++) {
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
        for (int i = 0; i < SLOT_COUNT; i++) {
            disabledNbt.putBoolean("slot" + i, disabledSlots[i]);
        }
        nbt.put("DisabledSlots", disabledNbt);
    }

    /**
     * 从 NBT 读取（包含自定义数据）
     */
    public void readNbt(NbtCompound nbt) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        NbtList list = nbt.getList("Upgrades", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound slotNbt = list.getCompound(i);
            int slot = slotNbt.getByte("Slot") & 255;
            if (slot >= 0 && slot < SLOT_COUNT && slotNbt.contains("id")) {
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
            for (int i = 0; i < SLOT_COUNT; i++) {
                disabledSlots[i] = disabledNbt.getBoolean("slot" + i);
            }
        } else {
            // 如果没有禁用状态数据，初始化为false
            for (int i = 0; i < SLOT_COUNT; i++) {
                disabledSlots[i] = false;
            }
        }
    }
}

