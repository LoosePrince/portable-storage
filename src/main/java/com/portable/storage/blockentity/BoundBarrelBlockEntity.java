package com.portable.storage.blockentity;

import com.portable.storage.PortableStorage;
import com.portable.storage.player.StorageGroupService;
import com.portable.storage.storage.StorageInventory;
import java.util.List;
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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 绑定木桶的方块实体
 * 实现 SidedInventory 以支持漏斗交互
 */
public class BoundBarrelBlockEntity extends LootableContainerBlockEntity implements SidedInventory {
    private DefaultedList<ItemStack> inventory;
    private UUID ownerUuid;
    private String ownerName;
    // 标记当前线程是否处于自动化（如漏斗）提取流程，用于 removeStack 决策
    private static final ThreadLocal<Boolean> THREAD_AUTOMATION_EXTRACT = ThreadLocal.withInitial(() -> false);
    // 记录当前线程本次自动化插入的来源方块位置（通常为漏斗位置），由 canInsert 写入、setStack 读取
    private static final ThreadLocal<BlockPos> THREAD_INSERT_SOURCE = new ThreadLocal<>();
    // 槽位5防重复处理：记录上一次在同一游戏刻处理的物品堆
    private long lastSlot5ProcessTick = -1L;
    private ItemStack lastSlot5Processed = ItemStack.EMPTY;
    private BlockPos lastSlot5SourcePos = null;

    public BoundBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOUND_BARREL, pos, state);
        // 0-4：标记槽位（显示在界面）
        // 5：隐藏输入槽位（仅用于漏斗/自动化插入）
        this.inventory = DefaultedList.ofSize(6, ItemStack.EMPTY);
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
            return Text.translatable("container.portable_storage.bound_barrel", ownerName);
        }
        return Text.translatable("container.portable_storage.bound_barrel.unknown");
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        // 使用漏斗界面：仅展示前5个标记槽位（0..4），隐藏第6个输入槽位（5）
        net.minecraft.inventory.Inventory visibleFiveSlotInventory = new net.minecraft.inventory.Inventory() {
            @Override
            public int size() { return 5; }

            @Override
            public boolean isEmpty() { return BoundBarrelBlockEntity.this.isEmpty() ||
                (getStack(0).isEmpty() && getStack(1).isEmpty() && getStack(2).isEmpty() && getStack(3).isEmpty() && getStack(4).isEmpty()); }

            @Override
            public ItemStack getStack(int slot) { return BoundBarrelBlockEntity.this.getStack(slot); }

            @Override
            public ItemStack removeStack(int slot, int amount) { return BoundBarrelBlockEntity.this.removeStack(slot, amount); }

            @Override
            public ItemStack removeStack(int slot) { return BoundBarrelBlockEntity.this.removeStack(slot); }

            @Override
            public void setStack(int slot, ItemStack stack) { BoundBarrelBlockEntity.this.setStack(slot, stack); }

            @Override
            public void markDirty() { BoundBarrelBlockEntity.this.markDirty(); }

            @Override
            public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return BoundBarrelBlockEntity.this.canPlayerUse(player); }

            @Override
            public void clear() {
                for (int i = 0; i < 5; i++) BoundBarrelBlockEntity.this.setStack(i, ItemStack.EMPTY);
            }
        };
        return new net.minecraft.screen.HopperScreenHandler(syncId, playerInventory, visibleFiveSlotInventory);
    }

    @Override
    public int size() {
        return 6;
    }

    // ==== SidedInventory 实现 ====

    @Override
    public int[] getAvailableSlots(Direction side) {
        // 允许自动化访问所有槽位，但通过 canInsert/canExtract 进行权限控制：
        // 0-4 仅可被提取（根据标记槽位从仓库输出），5 仅可被插入（存入仓库）
        if (ownerUuid != null) {
            return new int[]{0, 1, 2, 3, 4, 5};
        }
        // 未绑定时仅允许访问槽位0
        return new int[]{0};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        // 仅允许自动化（存在方向）向隐藏输入槽位5插入；标记槽位0-4不可插入
        if (ownerUuid == null || stack.isEmpty()) return false;
        if (slot != 5) return false;
        // dir != null 表示来自自动化方块（如漏斗/投掷器）
        if (dir != null) {
            // 记录来源位置：从本方块位置沿交互方向的相邻方块
            THREAD_INSERT_SOURCE.set(pos.offset(dir));
            return true;
        }
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // 槽位0-4：标记物品位
        if (slot >= 0 && slot <= 4 && ownerUuid != null) {
            ItemStack marker = getStack(slot);
            if (marker.isEmpty()) return false;
            if (dir != null) {
                // 漏斗操作：检查仓库中是否有对应物品
                if (world != null && world.getServer() != null) {
                    // 标记后续同线程 removeStack 为自动化提取
                    THREAD_AUTOMATION_EXTRACT.set(true);
                    // 检查仓库中是否有标记物品对应的物品
                    List<StorageInventory> storages = StorageGroupService.getStoragesByOwner(world.getServer(), ownerUuid);
                    for (StorageInventory storage : storages) {
                        for (int i = 0; i < storage.getCapacity(); i++) {
                            ItemStack disp = storage.getDisplayStack(i);
                            if (!disp.isEmpty() && ItemStack.areItemsAndComponentsEqual(disp, marker)) {
                                return true; // 仓库中有对应物品，允许提取
                            }
                        }
                    }
                }
                // 未查到物品时，撤销自动化标记
                THREAD_AUTOMATION_EXTRACT.set(false);
                return false; // 仓库中没有对应物品，不允许提取
            } else {
                // 玩家操作：允许取出标记物品本身
                return ItemStack.areItemsAndComponentsEqual(stack, marker);
            }
        }
        // 隐藏输入槽位5及其他：不允许被提取
        return false;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        // 槽位0-4的特殊处理（标记槽位）：
        if (ownerUuid != null && slot >= 0 && slot <= 4 && amount > 0) {
            ItemStack marker = getStack(slot);
            if (!marker.isEmpty() && world != null && world.getServer() != null) {
                boolean isAutomation = THREAD_AUTOMATION_EXTRACT.get();
                if (isAutomation) {
                    // 漏斗操作：从仓库提取物品
                    ItemStack want = marker.copy();
                    want.setCount(Math.min(amount, marker.getMaxCount()));

                    ItemStack taken = com.portable.storage.newstore.NewStoreService.takeFromNewStore(
                        world.getServer(),
                        ownerUuid,
                        want,
                        want.getCount()
                    );

                    if (!taken.isEmpty()) {
                        // 本次提取完成，清理自动化标记
                        THREAD_AUTOMATION_EXTRACT.set(false);
                        return taken;
                    }
                    // 如果仓库为空，不提取任何东西
                    THREAD_AUTOMATION_EXTRACT.set(false);
                    return ItemStack.EMPTY;
                } else {
                    // 玩家操作：取出标记物品本身
                    return super.removeStack(slot, amount);
                }
            }
        }

        // 默认行为：从内部库存取出
        return super.removeStack(slot, amount);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // 槽位5为隐藏输入槽位：仅用于自动化插入（UI 不暴露该槽位）
        if (ownerUuid != null && slot == 5 && !stack.isEmpty() && world != null && world.getServer() != null) {
            // 防止某些模组在同一游戏刻内以相同物品堆重复调用 setStack 导致重复入仓
            long nowTick = world.getTime();
            BlockPos sourcePos = THREAD_INSERT_SOURCE.get();
            if (nowTick == lastSlot5ProcessTick && !lastSlot5Processed.isEmpty()
                && ItemStack.areItemsAndComponentsEqual(stack, lastSlot5Processed)
                && stack.getCount() == lastSlot5Processed.getCount()
                && ((sourcePos == null && lastSlot5SourcePos == null) || (sourcePos != null && sourcePos.equals(lastSlot5SourcePos)))) {
                // 忽略重复调用，保持槽位不被写入，避免翻倍
                THREAD_INSERT_SOURCE.remove();
                return;
            }

            ItemStack toInsert = stack.copy();
            insertIntoOwnerStorage(toInsert);
            // 置空插入槽位（物品已被存入仓库）
            super.setStack(slot, ItemStack.EMPTY);
            markDirty();
            // 记录本次处理指纹
            lastSlot5ProcessTick = nowTick;
            lastSlot5Processed = stack.copy();
            lastSlot5SourcePos = sourcePos;
            THREAD_INSERT_SOURCE.remove();
            return;
        }

        // 槽位0-4为标记槽位：允许玩家直接设置/替换标记
        super.setStack(slot, stack);
    }

    /**
     * 将物品存入所有者的仓库（新版存储）
     */
    private void insertIntoOwnerStorage(ItemStack stack) {
        if (world == null || world.getServer() == null || ownerUuid == null) return;
        try {
            var server = world.getServer();
            var owner = server.getPlayerManager().getPlayer(ownerUuid);
            if (owner != null) {
                com.portable.storage.newstore.NewStoreService.insertForOnlinePlayer(owner, stack);
            } else {
                com.portable.storage.newstore.NewStoreService.insertForOfflineUuid(server, ownerUuid, stack, null);
            }
        } catch (Throwable e) {
            PortableStorage.LOGGER.error("Failed to insert item into owner's storage", e);
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
        inventory = DefaultedList.ofSize(size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, inventory, registryLookup);
    }
}

