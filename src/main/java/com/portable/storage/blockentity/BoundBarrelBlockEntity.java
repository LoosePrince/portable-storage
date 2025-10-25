package com.portable.storage.blockentity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.portable.storage.PortableStorage;
import com.portable.storage.newstore.NewStoreService;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 绑定木桶的方块实体 - 虚拟槽位版本
 * 实现虚拟槽位系统，批量结算，大幅提升性能
 */
public class BoundBarrelBlockEntity extends LootableContainerBlockEntity implements SidedInventory {
    private DefaultedList<ItemStack> inventory;
    private UUID ownerUuid;
    private String ownerName;
    
    // 批量处理相关
    private long lastBatchTime = 0;
    private long lastInputBatchTime = 0;
    private static final long BATCH_INTERVAL = 20; // 1秒 = 20游戏刻（输出）
    private static final long FORCE_INPUT_INTERVAL = 200; // 10秒 = 200游戏刻（强制输入）
    
    // 输入缓存：收集物品，批量处理
    private java.util.List<ItemStack> inputCache = new java.util.ArrayList<>();
    
    // 输入处理状态：避免重复处理
    private boolean hasInputItems = false;
    
    // 槽位布局：
    // 0-4: 输出槽位 (漏斗可提取，对应标记槽位10-14)
    // 5-9: 输入槽位 (漏斗可插入，对应5个面)
    // 10-14: 标记槽位 (玩家可见，用于设置标记)
    private static final int OUTPUT_SLOTS_START = 0;
    private static final int OUTPUT_SLOTS_END = 4;
    private static final int INPUT_SLOTS_START = 5;
    private static final int INPUT_SLOTS_END = 9;
    private static final int MARKER_SLOTS_START = 10;
    private static final int MARKER_SLOTS_END = 14;
    private static final int TOTAL_SLOTS = 15;

    public BoundBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOUND_BARREL, pos, state);
        this.inventory = DefaultedList.ofSize(TOTAL_SLOTS, ItemStack.EMPTY);
    }

    @Override
    protected DefaultedList<ItemStack> getHeldStacks() {
        return inventory;
    }

    @Override
    protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
        this.inventory = inventory;
    }

    @Override
    protected Text getContainerName() {
        if (ownerName != null) {
            return Text.translatable("container." + PortableStorage.MOD_ID + ".bound_barrel", ownerName);
        }
        return Text.translatable("container." + PortableStorage.MOD_ID + ".bound_barrel.unknown");
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        // 使用漏斗界面：仅展示标记槽位（10-14），隐藏虚拟槽位
        net.minecraft.inventory.Inventory visibleMarkerInventory = new net.minecraft.inventory.Inventory() {
            @Override
            public int size() { return 5; }

            @Override
            public boolean isEmpty() { 
                return getStack(10).isEmpty() && getStack(11).isEmpty() && 
                       getStack(12).isEmpty() && getStack(13).isEmpty() && getStack(14).isEmpty(); 
            }

            @Override
            public ItemStack getStack(int slot) { return BoundBarrelBlockEntity.this.getStack(slot + 10); }

            @Override
            public ItemStack removeStack(int slot, int amount) { return BoundBarrelBlockEntity.this.removeStack(slot + 10, amount); }

            @Override
            public ItemStack removeStack(int slot) { return BoundBarrelBlockEntity.this.removeStack(slot + 10); }

            @Override
            public void setStack(int slot, ItemStack stack) { BoundBarrelBlockEntity.this.setStack(slot + 10, stack); }

            @Override
            public void markDirty() { BoundBarrelBlockEntity.this.markDirty(); }

            @Override
            public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { 
                return BoundBarrelBlockEntity.this.canPlayerUse(player); 
            }

            @Override
            public void clear() {
                for (int i = 0; i < 5; i++) BoundBarrelBlockEntity.this.setStack(i + 10, ItemStack.EMPTY);
            }
        };
        return new net.minecraft.screen.HopperScreenHandler(syncId, playerInventory, visibleMarkerInventory);
    }

    @Override
    public int size() {
        return TOTAL_SLOTS;
    }

    /**
     * 方块实体 tick 方法
     */
    public static void tick(World world, BlockPos pos, BlockState state, BoundBarrelBlockEntity blockEntity) {
        if (blockEntity != null) {
            blockEntity.portableStorage$processBatchIfNeeded();
        }
    }

    // ==== SidedInventory 实现 ====

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (ownerUuid != null) {
            // 返回所有虚拟槽位：输出槽位(0-4) + 输入槽位(5-9)
            return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        }
        return new int[]{};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        // 只允许向输入槽位(5-9)插入
        if (ownerUuid == null || stack.isEmpty()) return false;
        return slot >= INPUT_SLOTS_START && slot <= INPUT_SLOTS_END;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // 只允许从输出槽位(0-4)提取
        if (ownerUuid == null) return false;
        return slot >= OUTPUT_SLOTS_START && slot <= OUTPUT_SLOTS_END && !stack.isEmpty();
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        // 虚拟槽位：直接移除，不需要查询仓库
        if (slot >= OUTPUT_SLOTS_START && slot <= OUTPUT_SLOTS_END) {
            return super.removeStack(slot, amount);
        }
        return super.removeStack(slot, amount);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // 输入槽位：直接设置，不立即存入仓库
        if (slot >= INPUT_SLOTS_START && slot <= INPUT_SLOTS_END) {
            super.setStack(slot, stack);
            // 更新输入状态：有物品时标记为需要处理
            hasInputItems = !stack.isEmpty();
            markDirty();
            return;
        }
        
        // 标记槽位：允许玩家直接设置/替换标记
        if (slot >= MARKER_SLOTS_START && slot <= MARKER_SLOTS_END) {
            super.setStack(slot, stack);
            markDirty();
            return;
        }
        
        super.setStack(slot, stack);
    }

    /**
     * 批量处理：检查是否需要执行批量操作
     */
    public void portableStorage$processBatchIfNeeded() {
        if (world == null || world.getServer() == null || ownerUuid == null) return;
        
        long currentTime = world.getTime();
        
        // 处理输入槽位：只在有物品时才处理，或10秒强制处理
        boolean shouldProcessInput = hasInputItems && (currentTime - lastInputBatchTime >= FORCE_INPUT_INTERVAL);
        
        if (shouldProcessInput) {
            processInputSlots();
            lastInputBatchTime = currentTime;
        }
        
        // 处理输出槽位
        if (currentTime - lastBatchTime >= BATCH_INTERVAL) {
            processOutputSlots();
            lastBatchTime = currentTime;
        }
    }


    /**
     * 处理输入槽位：将物品存入仓库（与输出处理一致的性能）
     * 只有槽位满了或10秒到了才存入，避免频繁调用存储系统
     */
    private void processInputSlots() {
        // 快速检查：如果没有物品，直接返回（大部分时候都是这种情况）
        if (!hasInputItems) {
            return;
        }
        
        // 简化逻辑：直接处理所有物品，让调用方控制触发条件
        
        // 收集所有需要处理的物品
        inputCache.clear();
        boolean foundItems = false;
        for (int slot = INPUT_SLOTS_START; slot <= INPUT_SLOTS_END; slot++) {
            ItemStack stack = getStack(slot);
            if (!stack.isEmpty()) {
                inputCache.add(stack.copy());
                setStack(slot, ItemStack.EMPTY); // 清空槽位
                foundItems = true;
            }
        }
        
        // 更新状态
        hasInputItems = foundItems;
        
        // 批量处理：减少存储系统调用次数
        if (!inputCache.isEmpty()) {
            batchInsertIntoOwnerStorage(inputCache);
        }
        
        // 注意：时间更新在调用方处理，这里不需要重复更新
    }

    /**
     * 处理输出槽位：从仓库填充物品
     */
    private void processOutputSlots() {
        for (int slot = OUTPUT_SLOTS_START; slot <= OUTPUT_SLOTS_END; slot++) {
            int markerSlot = slot + 10; // 对应的标记槽位
            ItemStack marker = getStack(markerSlot);
            if (marker.isEmpty()) {
                // 没有标记，清空输出槽位
                setStack(slot, ItemStack.EMPTY);
                continue;
            }
            
            ItemStack currentOutput = getStack(slot);
            if (currentOutput.isEmpty() || !ItemStack.areItemsAndComponentsEqual(currentOutput, marker)) {
                // 输出槽位为空或物品不匹配，从仓库取出一组
                ItemStack want = marker.copy();
                want.setCount(marker.getMaxCount());
                
                ItemStack taken = NewStoreService.takeFromNewStore(
                    world.getServer(),
                    ownerUuid,
                    want,
                    want.getCount()
                );
                
                if (!taken.isEmpty()) {
                    setStack(slot, taken);
                } else {
                    setStack(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    /**
     * 批量将物品存入所有者的仓库（优化版本）
     */
    private void batchInsertIntoOwnerStorage(java.util.List<ItemStack> items) {
        if (world == null || world.getServer() == null || ownerUuid == null || items.isEmpty()) return;
        try {
            var server = world.getServer();
            // 批量处理：减少存储系统调用次数
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    NewStoreService.insertForOfflineUuid(server, ownerUuid, stack, null);
                }
            }
        } catch (Throwable e) {
            PortableStorage.LOGGER.error("Failed to batch insert items into owner's storage", e);
        }
    }

    /**
     * 将物品存入所有者的仓库（优化版本，避免网络同步）
     */
    private void insertIntoOwnerStorage(ItemStack stack) {
        if (world == null || world.getServer() == null || ownerUuid == null) return;
        try {
            var server = world.getServer();
            // 统一使用离线版本，避免网络同步的性能开销
            NewStoreService.insertForOfflineUuid(server, ownerUuid, stack, null);
        } catch (Throwable e) {
            PortableStorage.LOGGER.error("Failed to insert item into owner's storage", e);
        }
    }

    /**
     * 归还输出槽位的物品到仓库
     * 在绑定木桶被破坏或标记物被拿走/更换时调用
     */
    public void returnOutputItemsToStorage() {
        if (world == null || world.getServer() == null || ownerUuid == null) return;
        
        for (int slot = OUTPUT_SLOTS_START; slot <= OUTPUT_SLOTS_END; slot++) {
            ItemStack stack = getStack(slot);
            if (!stack.isEmpty()) {
                insertIntoOwnerStorage(stack);
                setStack(slot, ItemStack.EMPTY);
            }
        }
    }


    // ==== 绑定信息管理 ====

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name;
        markDirty();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void copyDataToItem(ItemStack stack) {
        if (ownerUuid == null) return;

        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("ps_owner_uuid", ownerUuid);
        nbt.putLong("ps_owner_uuid_most", ownerUuid.getMostSignificantBits());
        nbt.putLong("ps_owner_uuid_least", ownerUuid.getLeastSignificantBits());
        
        if (ownerName != null) {
            nbt.putString("ps_owner_name", ownerName);
        }

        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        if (ownerUuid != null) {
            nbt.putUuid("ps_owner_uuid", ownerUuid);
        }
        if (ownerName != null) {
            nbt.putString("ps_owner_name", ownerName);
        }
        nbt.putLong("last_batch_time", lastBatchTime);
        nbt.putLong("last_input_batch_time", lastInputBatchTime);
        // 注意：inputCache 不需要持久化，每次重启时清空即可
        Inventories.writeNbt(nbt, inventory, registryLookup);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        if (nbt.containsUuid("ps_owner_uuid")) {
            ownerUuid = nbt.getUuid("ps_owner_uuid");
        }
        if (nbt.contains("ps_owner_name")) {
            ownerName = nbt.getString("ps_owner_name");
        }
        if (nbt.contains("last_batch_time")) {
            lastBatchTime = nbt.getLong("last_batch_time");
        }
        if (nbt.contains("last_input_batch_time")) {
            lastInputBatchTime = nbt.getLong("last_input_batch_time");
        }
        inventory = DefaultedList.ofSize(size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, inventory, registryLookup);
    }
}


