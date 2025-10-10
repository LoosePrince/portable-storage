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
import net.minecraft.screen.GenericContainerScreenHandler;
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
        this.inventory = DefaultedList.ofSize(27, ItemStack.EMPTY); // 标准木桶大小
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
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
    }

    @Override
    public int size() {
        return 27;
    }

    // ==== SidedInventory 实现 ====

    @Override
    public int[] getAvailableSlots(Direction side) {
        // 槽位0用于提取（标记物品），其他槽位用于插入
        if (ownerUuid != null) {
            return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
        }
        return new int[]{0};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        // 只允许插入到非0槽位（0号槽位是标记槽位）
        return ownerUuid != null && slot != 0 && !stack.isEmpty();
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // 槽位0：标记物品，可以被玩家提取，但漏斗只能在有标记物品时提取仓库物品
        if (slot == 0 && ownerUuid != null) {
            ItemStack marker = getStack(0);
            if (marker.isEmpty()) return false;

            // 如果是漏斗操作（有方向），只允许提取仓库物品
            // 如果是玩家操作（无方向），允许提取标记物品
            if (dir != null) {
                // 漏斗操作：检查是否有对应物品在仓库中
                return true; // 总是允许，具体检查在removeStack中
            } else {
                // 玩家操作：允许提取标记物品
                return ItemStack.areItemsAndComponentsEqual(stack, marker);
            }
        }
        return false;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        // 槽位0的特殊处理
        if (ownerUuid != null && slot == 0 && amount > 0) {
            ItemStack marker = getStack(0);
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

    @Override
    public void setStack(int slot, ItemStack stack) {
        // 拦截设置物品的操作，如果不是标记槽位，则存入仓库
		if (ownerUuid != null && slot != 0 && !stack.isEmpty() && world != null && world.getServer() != null) {
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

        // 标记槽位正常设置
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

