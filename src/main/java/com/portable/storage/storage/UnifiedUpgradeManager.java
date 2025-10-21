package com.portable.storage.storage;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;

/**
 * 统一升级槽位管理器
 * 管理基础升级槽位、扩展升级槽位、垃圾桶槽位和流体槽位
 */
public class UnifiedUpgradeManager {
    // 槽位数量常量
    private static final int BASE_SLOT_COUNT = 5;
    private static final int EXTENDED_SLOT_COUNT = 5;
    
    // 基础升级槽位（0-4）
    private final ItemStack[] baseSlots;
    private final boolean[] baseSlotDisabled;
    
    // 扩展升级槽位（独立序号0-4）
    private final ItemStack[] extendedSlots;
    private final boolean[] extendedSlotDisabled;
    
    // 特殊槽位
    private ItemStack trashSlot = ItemStack.EMPTY; // 垃圾桶槽位
    private boolean trashSlotDisabled = false;
    
    private ItemStack fluidSlot = ItemStack.EMPTY; // 流体槽位
    private int lavaUnits = 0;
    private int waterUnits = 0;
    private int milkUnits = 0;
    
    // 附魔之瓶相关
    private long xpPool = 0L;
    private boolean levelMaintenanceEnabled = false;
    
    public UnifiedUpgradeManager() {
        this.baseSlots = new ItemStack[BASE_SLOT_COUNT];
        this.baseSlotDisabled = new boolean[BASE_SLOT_COUNT];
        this.extendedSlots = new ItemStack[EXTENDED_SLOT_COUNT];
        this.extendedSlotDisabled = new boolean[EXTENDED_SLOT_COUNT];
        
        for (int i = 0; i < BASE_SLOT_COUNT; i++) {
            this.baseSlots[i] = ItemStack.EMPTY;
            this.baseSlotDisabled[i] = false;
        }
        
        for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
            this.extendedSlots[i] = ItemStack.EMPTY;
            this.extendedSlotDisabled[i] = false;
        }
    }
    
    // ===== 基础槽位管理 =====
    
    /**
     * 获取基础槽位物品
     */
    public ItemStack getBaseSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= BASE_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return baseSlots[slotIndex];
    }
    
    /**
     * 设置基础槽位物品
     */
    public void setBaseSlot(int slotIndex, ItemStack stack) {
        if (slotIndex < 0 || slotIndex >= BASE_SLOT_COUNT) {
            return;
        }
        this.baseSlots[slotIndex] = stack;
    }
    
    /**
     * 检查基础槽位是否被禁用
     */
    public boolean isBaseSlotDisabled(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= BASE_SLOT_COUNT) {
            return false;
        }
        return baseSlotDisabled[slotIndex];
    }
    
    /**
     * 设置基础槽位禁用状态
     */
    public void setBaseSlotDisabled(int slotIndex, boolean disabled) {
        if (slotIndex < 0 || slotIndex >= BASE_SLOT_COUNT) {
            return;
        }
        this.baseSlotDisabled[slotIndex] = disabled;
    }
    
    /**
     * 切换基础槽位禁用状态
     */
    public void toggleBaseSlotDisabled(int slotIndex) {
        setBaseSlotDisabled(slotIndex, !isBaseSlotDisabled(slotIndex));
    }
    
    // ===== 扩展槽位管理 =====
    
    /**
     * 获取扩展槽位物品
     */
    public ItemStack getExtendedSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= EXTENDED_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return extendedSlots[slotIndex];
    }
    
    /**
     * 设置扩展槽位物品
     */
    public void setExtendedSlot(int slotIndex, ItemStack stack) {
        if (slotIndex < 0 || slotIndex >= EXTENDED_SLOT_COUNT) {
            return;
        }
        this.extendedSlots[slotIndex] = stack;
    }
    
    /**
     * 检查扩展槽位是否被禁用
     */
    public boolean isExtendedSlotDisabled(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= EXTENDED_SLOT_COUNT) {
            return false;
        }
        return extendedSlotDisabled[slotIndex];
    }
    
    /**
     * 设置扩展槽位禁用状态
     */
    public void setExtendedSlotDisabled(int slotIndex, boolean disabled) {
        if (slotIndex < 0 || slotIndex >= EXTENDED_SLOT_COUNT) {
            return;
        }
        this.extendedSlotDisabled[slotIndex] = disabled;
    }
    
    /**
     * 切换扩展槽位禁用状态
     */
    public void toggleExtendedSlotDisabled(int slotIndex) {
        setExtendedSlotDisabled(slotIndex, !isExtendedSlotDisabled(slotIndex));
    }
    
    // ===== 特殊槽位管理 =====
    
    /**
     * 获取垃圾桶槽位物品
     */
    public ItemStack getTrashSlot() {
        return trashSlot;
    }
    
    /**
     * 设置垃圾桶槽位物品
     */
    public void setTrashSlot(ItemStack stack) {
        this.trashSlot = stack;
    }
    
    /**
     * 检查垃圾桶槽位是否被禁用
     */
    public boolean isTrashSlotDisabled() {
        return trashSlotDisabled;
    }
    
    /**
     * 设置垃圾桶槽位禁用状态
     */
    public void setTrashSlotDisabled(boolean disabled) {
        this.trashSlotDisabled = disabled;
    }
    
    /**
     * 切换垃圾桶槽位禁用状态
     */
    public void toggleTrashSlotDisabled() {
        this.trashSlotDisabled = !this.trashSlotDisabled;
    }
    
    /**
     * 获取流体槽位物品
     */
    public ItemStack getFluidSlot() {
        return fluidSlot;
    }
    
    /**
     * 设置流体槽位物品
     */
    public void setFluidSlot(ItemStack stack) {
        this.fluidSlot = stack;
    }
    
    // ===== 升级状态检查 =====
    
    /**
     * 检查箱子升级是否激活
     */
    public boolean isChestUpgradeActive() {
        ItemStack chestStack = getBaseSlot(2); // 槽位2是箱子
        return !chestStack.isEmpty() && !isBaseSlotDisabled(2);
    }
    
    /**
     * 检查扩展槽位是否可用（需要箱子升级激活）
     */
    public boolean isExtendedSlotEnabled(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= EXTENDED_SLOT_COUNT) {
            return false;
        }
        return isChestUpgradeActive() && !isExtendedSlotDisabled(slotIndex);
    }
    
    /**
     * 检查光灵箭升级是否激活
     */
    public boolean isSpectralArrowUpgradeActive() {
        ItemStack stack = getExtendedSlot(0); // 扩展槽位0是光灵箭
        return !stack.isEmpty() && !isExtendedSlotDisabled(0);
    }
    
    /**
     * 检查床升级是否激活
     */
    public boolean isBedUpgradeActive() {
        ItemStack stack = getExtendedSlot(1); // 扩展槽位1是床
        return !stack.isEmpty() && !isExtendedSlotDisabled(1);
    }
    
    /**
     * 检查附魔之瓶升级是否激活
     */
    public boolean isXpBottleUpgradeActive() {
        ItemStack stack = getExtendedSlot(2); // 扩展槽位2是附魔之瓶
        return !stack.isEmpty() && !isExtendedSlotDisabled(2);
    }
    
    /**
     * 检查活塞升级是否激活
     */
    public boolean isPistonUpgradeActive() {
        ItemStack stack = getExtendedSlot(3); // 扩展槽位3是活塞
        return !stack.isEmpty() && !isExtendedSlotDisabled(3);
    }
    
    /**
     * 检查垃圾桶槽位是否激活
     */
    public boolean isTrashSlotActive() {
        return !trashSlot.isEmpty() && !trashSlotDisabled;
    }
    
    // ===== 物品验证和插入 =====
    
    /**
     * 检查物品是否适合基础槽位
     */
    public boolean isValidForBaseSlot(int slotIndex, ItemStack stack) {
        UpgradeSlotType slotType = UpgradeSlotType.getBaseSlotType(slotIndex);
        return slotType != null && slotType.isValidItem(stack);
    }
    
    /**
     * 检查物品是否适合扩展槽位
     */
    public boolean isValidForExtendedSlot(int slotIndex, ItemStack stack) {
        UpgradeSlotType slotType = UpgradeSlotType.getExtendedSlotType(slotIndex);
        return slotType != null && slotType.isValidItem(stack);
    }
    
    /**
     * 尝试插入物品到基础槽位
     */
    public boolean tryInsertBaseSlot(int slotIndex, ItemStack stack, java.util.UUID playerUuid, String playerName) {
        if (!isValidForBaseSlot(slotIndex, stack) || !getBaseSlot(slotIndex).isEmpty() || stack.getCount() != 1) {
            return false;
        }
        
        ItemStack toInsert = stack.copy();
        
        // 特殊处理：木桶绑定逻辑（槽位3是木桶）
        if (slotIndex == 3 && toInsert.isOf(Items.BARREL)) {
            // 检查是否已经绑定
            NbtComponent customData = toInsert.get(DataComponentTypes.CUSTOM_DATA);
            boolean alreadyBound = false;
            
            if (customData != null) {
                NbtCompound nbt = customData.copyNbt();
                if (nbt.containsUuid("ps_owner_uuid") || 
                    (nbt.contains("ps_owner_uuid_most") && nbt.contains("ps_owner_uuid_least"))) {
                    alreadyBound = true;
                }
            }
            
            // 如果没有绑定，则绑定到当前玩家
            if (!alreadyBound && playerUuid != null) {
                NbtCompound customNbt = new NbtCompound();
                customNbt.putUuid("ps_owner_uuid", playerUuid);
                customNbt.putLong("ps_owner_uuid_most", playerUuid.getMostSignificantBits());
                customNbt.putLong("ps_owner_uuid_least", playerUuid.getLeastSignificantBits());
                
                if (playerName != null) {
                    customNbt.putString("ps_owner_name", playerName);
                }
                
                // 只设置 CUSTOM_DATA，不设置 BLOCK_ENTITY_DATA
                // BLOCK_ENTITY_DATA 需要实体ID，会导致序列化错误
                toInsert.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customNbt));
                toInsert.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
        }
        
        setBaseSlot(slotIndex, toInsert);
        return true;
    }
    
    /**
     * 尝试插入物品到扩展槽位
     */
    public boolean tryInsertExtendedSlot(int slotIndex, ItemStack stack) {
        if (!isValidForExtendedSlot(slotIndex, stack) || !getExtendedSlot(slotIndex).isEmpty() || stack.getCount() != 1) {
            return false;
        }
        setExtendedSlot(slotIndex, stack.copy());
        return true;
    }
    
    /**
     * 尝试插入物品到垃圾桶槽位
     */
    public boolean tryInsertTrashSlot(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // 垃圾桶槽位接受任何物品，最大一组，覆盖机制
        int maxCount = Math.min(stack.getCount(), stack.getMaxCount());
        ItemStack trashStack = stack.copy();
        trashStack.setCount(maxCount);
        setTrashSlot(trashStack);
        return true;
    }
    
    /**
     * 从基础槽位取出物品
     */
    public ItemStack takeBaseSlot(int slotIndex) {
        ItemStack result = getBaseSlot(slotIndex);
        setBaseSlot(slotIndex, ItemStack.EMPTY);
        return result;
    }
    
    /**
     * 从扩展槽位取出物品
     */
    public ItemStack takeExtendedSlot(int slotIndex) {
        ItemStack result = getExtendedSlot(slotIndex);
        setExtendedSlot(slotIndex, ItemStack.EMPTY);
        return result;
    }
    
    /**
     * 从垃圾桶槽位取出物品
     */
    public ItemStack takeTrashSlot() {
        ItemStack result = getTrashSlot();
        setTrashSlot(ItemStack.EMPTY);
        return result;
    }
    
    // ===== 流体管理 =====
    
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
    
    // ===== 附魔之瓶经验池管理 =====
    
    public long getXpPool() { 
        return Math.max(0L, xpPool); 
    }

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

    // ===== 等级维持状态管理 =====
    
    public boolean isLevelMaintenanceEnabled() { 
        return levelMaintenanceEnabled; 
    }
    
    public void setLevelMaintenanceEnabled(boolean enabled) { 
        levelMaintenanceEnabled = enabled; 
    }
    
    public void toggleLevelMaintenance() { 
        levelMaintenanceEnabled = !levelMaintenanceEnabled; 
    }
    
    // ===== NBT 序列化 =====
    
    /**
     * 保存到 NBT
     */
    public void writeNbt(NbtCompound nbt) {
        // 保存基础槽位
        NbtList baseList = new NbtList();
        for (int i = 0; i < BASE_SLOT_COUNT; i++) {
            if (!baseSlots[i].isEmpty()) {
                NbtCompound slotNbt = new NbtCompound();
                slotNbt.putByte("Slot", (byte) i);
                slotNbt.putString("id", Registries.ITEM.getId(baseSlots[i].getItem()).toString());
                slotNbt.putByte("Count", (byte) baseSlots[i].getCount());
                // 写入自定义数据
                NbtComponent custom = baseSlots[i].get(DataComponentTypes.CUSTOM_DATA);
                if (custom != null) {
                    slotNbt.put("CustomData", custom.copyNbt());
                }
                NbtComponent be = baseSlots[i].get(DataComponentTypes.BLOCK_ENTITY_DATA);
                if (be != null) {
                    slotNbt.put("BlockEntityData", be.copyNbt());
                }
                baseList.add(slotNbt);
            }
        }
        nbt.put("BaseUpgrades", baseList);
        
        // 保存基础槽位禁用状态
        NbtCompound baseDisabledNbt = new NbtCompound();
        for (int i = 0; i < BASE_SLOT_COUNT; i++) {
            baseDisabledNbt.putBoolean("slot" + i, baseSlotDisabled[i]);
        }
        nbt.put("BaseDisabledSlots", baseDisabledNbt);
        
        // 保存扩展槽位
        NbtList extendedList = new NbtList();
        for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
            if (!extendedSlots[i].isEmpty()) {
                NbtCompound slotNbt = new NbtCompound();
                slotNbt.putByte("Slot", (byte) i);
                slotNbt.putString("id", Registries.ITEM.getId(extendedSlots[i].getItem()).toString());
                slotNbt.putByte("Count", (byte) extendedSlots[i].getCount());
                // 写入自定义数据
                NbtComponent custom = extendedSlots[i].get(DataComponentTypes.CUSTOM_DATA);
                if (custom != null) {
                    slotNbt.put("CustomData", custom.copyNbt());
                }
                NbtComponent be = extendedSlots[i].get(DataComponentTypes.BLOCK_ENTITY_DATA);
                if (be != null) {
                    slotNbt.put("BlockEntityData", be.copyNbt());
                }
                extendedList.add(slotNbt);
            }
        }
        nbt.put("ExtendedUpgrades", extendedList);
        
        // 保存扩展槽位禁用状态
        NbtCompound extendedDisabledNbt = new NbtCompound();
        for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
            extendedDisabledNbt.putBoolean("slot" + i, extendedSlotDisabled[i]);
        }
        nbt.put("ExtendedDisabledSlots", extendedDisabledNbt);
        
        // 保存垃圾桶槽位
        if (!trashSlot.isEmpty()) {
            NbtCompound trashNbt = new NbtCompound();
            trashNbt.putString("id", Registries.ITEM.getId(trashSlot.getItem()).toString());
            trashNbt.putByte("Count", (byte) trashSlot.getCount());
            NbtComponent custom = trashSlot.get(DataComponentTypes.CUSTOM_DATA);
            if (custom != null) {
                trashNbt.put("CustomData", custom.copyNbt());
            }
            nbt.put("TrashSlot", trashNbt);
        }
        nbt.putBoolean("TrashSlotDisabled", trashSlotDisabled);
        
        // 保存流体槽位
        if (!fluidSlot.isEmpty()) {
            NbtCompound fluidNbt = new NbtCompound();
            fluidNbt.putString("id", Registries.ITEM.getId(fluidSlot.getItem()).toString());
            fluidNbt.putByte("Count", (byte) fluidSlot.getCount());
            NbtComponent custom = fluidSlot.get(DataComponentTypes.CUSTOM_DATA);
            if (custom != null) {
                fluidNbt.put("CustomData", custom.copyNbt());
            }
            nbt.put("FluidSlot", fluidNbt);
        }
        
        // 保存流体单位
        nbt.putInt("LavaUnits", lavaUnits);
        nbt.putInt("WaterUnits", waterUnits);
        nbt.putInt("MilkUnits", milkUnits);
        
        // 保存附魔之瓶相关
        nbt.putLong("XpPool", Math.max(0L, xpPool));
        nbt.putBoolean("LevelMaintenanceEnabled", levelMaintenanceEnabled);
    }
    
    /**
     * 从 NBT 读取
     */
    public void readNbt(NbtCompound nbt) {
        // 读取基础槽位
        for (int i = 0; i < BASE_SLOT_COUNT; i++) {
            baseSlots[i] = ItemStack.EMPTY;
        }
        NbtList baseList = nbt.getList("BaseUpgrades", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < baseList.size(); i++) {
            NbtCompound slotNbt = baseList.getCompound(i);
            int slot = slotNbt.getByte("Slot") & 255;
            if (slot >= 0 && slot < BASE_SLOT_COUNT && slotNbt.contains("id")) {
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
                        baseSlots[slot] = stack;
                    }
                }
            }
        }
        
        // 读取基础槽位禁用状态
        if (nbt.contains("BaseDisabledSlots", NbtElement.COMPOUND_TYPE)) {
            NbtCompound baseDisabledNbt = nbt.getCompound("BaseDisabledSlots");
            for (int i = 0; i < BASE_SLOT_COUNT; i++) {
                baseSlotDisabled[i] = baseDisabledNbt.getBoolean("slot" + i);
            }
        } else {
            for (int i = 0; i < BASE_SLOT_COUNT; i++) {
                baseSlotDisabled[i] = false;
            }
        }
        
        // 读取扩展槽位
        for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
            extendedSlots[i] = ItemStack.EMPTY;
        }
        NbtList extendedList = nbt.getList("ExtendedUpgrades", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < extendedList.size(); i++) {
            NbtCompound slotNbt = extendedList.getCompound(i);
            int slot = slotNbt.getByte("Slot") & 255;
            if (slot >= 0 && slot < EXTENDED_SLOT_COUNT && slotNbt.contains("id")) {
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
                        extendedSlots[slot] = stack;
                    }
                }
            }
        }
        
        // 读取扩展槽位禁用状态
        if (nbt.contains("ExtendedDisabledSlots", NbtElement.COMPOUND_TYPE)) {
            NbtCompound extendedDisabledNbt = nbt.getCompound("ExtendedDisabledSlots");
            for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
                extendedSlotDisabled[i] = extendedDisabledNbt.getBoolean("slot" + i);
            }
        } else {
            for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
                extendedSlotDisabled[i] = false;
            }
        }
        
        // 读取垃圾桶槽位
        if (nbt.contains("TrashSlot", NbtElement.COMPOUND_TYPE)) {
            NbtCompound trashNbt = nbt.getCompound("TrashSlot");
            if (trashNbt.contains("id")) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(trashNbt.getString("id"));
                if (id != null) {
                    net.minecraft.item.Item item = Registries.ITEM.get(id);
                    if (item != null && item != Items.AIR) {
                        ItemStack stack = new ItemStack(item);
                        stack.setCount(trashNbt.getByte("Count"));
                        if (trashNbt.contains("CustomData", NbtElement.COMPOUND_TYPE)) {
                            NbtCompound custom = trashNbt.getCompound("CustomData");
                            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));
                        }
                        trashSlot = stack;
                    }
                }
            }
        } else {
            trashSlot = ItemStack.EMPTY;
        }
        trashSlotDisabled = nbt.getBoolean("TrashSlotDisabled");
        
        // 读取流体槽位
        if (nbt.contains("FluidSlot", NbtElement.COMPOUND_TYPE)) {
            NbtCompound fluidNbt = nbt.getCompound("FluidSlot");
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
                        fluidSlot = stack;
                    }
                }
            }
        } else {
            fluidSlot = ItemStack.EMPTY;
        }
        
        // 读取流体单位
        lavaUnits = nbt.getInt("LavaUnits");
        waterUnits = nbt.getInt("WaterUnits");
        milkUnits = nbt.getInt("MilkUnits");
        
        // 读取附魔之瓶相关
        if (nbt.contains("XpPool", NbtElement.LONG_TYPE)) {
            xpPool = Math.max(0L, nbt.getLong("XpPool"));
        } else {
            xpPool = 0L;
        }
        if (nbt.contains("LevelMaintenanceEnabled", 1)) {
            levelMaintenanceEnabled = nbt.getBoolean("LevelMaintenanceEnabled");
        } else {
            levelMaintenanceEnabled = false;
        }
    }
    
    // ===== 兼容性方法 =====
    
    /**
     * 获取所有扩展槽位中的物品（用于箱子升级取消时掉落）
     */
    public java.util.List<ItemStack> getExtendedSlotItems() {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
            if (!extendedSlots[i].isEmpty()) {
                items.add(extendedSlots[i].copy());
            }
        }
        return items;
    }
    
    /**
     * 清空所有扩展槽位
     */
    public void clearExtendedSlots() {
        for (int i = 0; i < EXTENDED_SLOT_COUNT; i++) {
            extendedSlots[i] = ItemStack.EMPTY;
        }
    }
}
