package com.portable.storage.blockentity;

import com.portable.storage.PortableStorage;
import net.minecraft.block.HopperBlock;
import com.portable.storage.player.StorageGroupService;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
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
        // 仅允许漏斗/投掷器插入到隐藏输入槽位5；标记槽位0-4不可插入
        if (ownerUuid == null || stack.isEmpty()) return false;
        if (slot != 5) return false;
        // 通过调用栈限定仅自动化（漏斗/投掷器）可插入
        return isAutomationInsertionOperation();
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // 槽位0-4：标记物品位
        if (slot >= 0 && slot <= 4 && ownerUuid != null) {
            ItemStack marker = getStack(slot);
            if (marker.isEmpty()) return false;
            if (dir != null) {
                // 漏斗操作：允许提取（实际物品由 removeStack 决定，从仓库输出）
                return true;
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
                // 检查是否是漏斗操作（通过调用栈检查）
                boolean isHopperOperation = isHopperOperation();

                if (isHopperOperation) {
                    // 漏斗操作：从仓库提取物品
                    ItemStack want = marker.copy();
                    want.setCount(Math.min(amount, marker.getMaxCount()));

                    ItemStack taken = StorageGroupService.takeFromOwnerGroup(
                        world.getServer(),
                        ownerUuid,
                        want,
                        want.getCount()
                    );

                    if (!taken.isEmpty()) {
                        // PortableStorage.LOGGER.info("Hopper extracted {} x{} from bound barrel",
                            // taken.getItem(), taken.getCount());
                        return taken;
                    }
                    // 如果仓库为空，不提取任何东西
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

    /**
     * 检查当前操作是否来自漏斗
     */
    private boolean isHopperOperation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.contains("Hopper") || className.contains("hopper")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前插入操作是否来自自动化（漏斗或投掷器）。
     */
    private boolean isAutomationInsertionOperation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            // 漏斗
            if (className.contains("Hopper") || className.contains("hopper")) {
                return true;
            }
            // 投掷器（Dropper）；发射器（Dispenser）不会尝试插入容器
            if (className.contains("Dropper") || className.contains("dropper")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // 槽位5为隐藏输入槽位：仅允许漏斗/投掷器来源插入；其他来源一律拒绝
        if (ownerUuid != null && slot == 5 && !stack.isEmpty() && world != null && world.getServer() != null) {
            if (!isAutomationInsertionOperation()) {
                // 非自动化来源：忽略本次设置，避免被强行写入隐藏输入槽
                return;
            }
            ItemStack toInsert = stack.copy();
            // 来自漏斗时，尝试从相邻、面向本方块的漏斗一次性抽取同类物品，合并为一组
            if (isHopperOperation()) {
                tryBatchDrainFromAdjacentHopper(toInsert);
            }
            insertIntoOwnerStorage(toInsert);
            // 置空插入槽位（物品已被存入仓库）
            super.setStack(slot, ItemStack.EMPTY);
            markDirty();
            return;
        }

        // 槽位0-4为标记槽位：允许玩家直接设置/替换标记
        super.setStack(slot, stack);
    }

	/**
	 * 若此次插入来自漏斗，则尝试从面向本方块的相邻漏斗中一次性额外抽取同类物品，
	 * 将传入物品补足到最大堆叠（或直到漏斗耗尽）。
	 */
	private void tryBatchDrainFromAdjacentHopper(ItemStack accumulating) {
		if (world == null || accumulating.isEmpty()) return;
		// 需要补足的数量
		int targetMax = Math.min(accumulating.getMaxCount(), accumulating.getItem().getMaxCount());
		int need = targetMax - accumulating.getCount();
		if (need <= 0) return;

		// 扫描六个相邻方块，寻找输出口朝向本方块的漏斗
		for (Direction dir : Direction.values()) {
			BlockPos neighborPos = pos.offset(dir);
			BlockState neighborState = world.getBlockState(neighborPos);
			BlockEntity be = world.getBlockEntity(neighborPos);
			if (!(be instanceof net.minecraft.block.entity.HopperBlockEntity hopper)) continue;

			// 判断该漏斗是否将物品输出到本方块
			Direction facing;
			try {
				facing = neighborState.get(HopperBlock.FACING);
			} catch (Exception e) {
				// 属性不存在时保守跳过
				continue;
			}
			BlockPos outputPos = neighborPos.offset(facing);
			if (!outputPos.equals(this.pos)) continue;

			// 匹配并从漏斗的物品栏中提取同类物品
			Inventory hopperInv = hopper;
			for (int slot = 0; slot < hopperInv.size() && need > 0; slot++) {
				ItemStack inHopper = hopperInv.getStack(slot);
				if (inHopper.isEmpty()) continue;
				if (!ItemStack.areItemsAndComponentsEqual(inHopper, accumulating)) continue;

				int move = Math.min(need, inHopper.getCount());
				if (move <= 0) continue;
				inHopper.decrement(move);
				hopperInv.setStack(slot, inHopper.isEmpty() ? ItemStack.EMPTY : inHopper);
				accumulating.increment(move);
				need -= move;
			}

			// 已满足或已尽力，刷新漏斗状态
			hopper.markDirty();
			if (need <= 0) break;
		}
	}

    /**
     * 将物品存入所有者的仓库
     */
    private void insertIntoOwnerStorage(ItemStack stack) {
        if (world == null || world.getServer() == null || ownerUuid == null) return;

        try {
            var server = world.getServer();
            var playerManager = server.getPlayerManager();
            var owner = playerManager.getPlayer(ownerUuid);

            if (owner != null) {
                // 在线玩家：直接存入
                var storage = com.portable.storage.player.PlayerStorageService.getInventory(owner);
                storage.insertItemStack(stack, System.currentTimeMillis());
                // 发送同步消息给客户端
                com.portable.storage.net.ServerNetworkingHandlers.sendIncrementalSyncOnDemand(owner);
                // PortableStorage.LOGGER.info("Inserted {} x{} into owner's storage", stack.getItem(), stack.getCount());
            } else {
                // 离线玩家：加载并保存
                var storage = com.portable.storage.player.StoragePersistence.loadStorage(server, ownerUuid);
                storage.insertItemStack(stack, System.currentTimeMillis());
                com.portable.storage.player.StoragePersistence.saveStorage(server, ownerUuid, storage);
                // PortableStorage.LOGGER.info("Inserted {} x{} into offline owner's storage", stack.getItem(), stack.getCount());
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

