package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.config.ServerConfig;
import com.portable.storage.newstore.NewStoreService;
import com.portable.storage.player.PlayerEnablementState;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.StorageType;
import com.portable.storage.storage.UpgradeInventory;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * 将随身仓库以字段的形式附着在玩家上，并随 Player NBT 读写。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements PlayerStorageAccess {

	@Unique
	private StorageInventory portableStorage$inventory;
	
	@Unique
	private UpgradeInventory portableStorage$upgradeInventory;
	
	@Unique
	private boolean portableStorage$enabled = false;
	
	@Unique
	private StorageType portableStorage$storageType = StorageType.FULL;

	@Unique
	private static final String PORTABLE_STORAGE_NBT = "portable_storage";
	
	@Unique
	private static final String PORTABLE_STORAGE_UPGRADES_NBT = "portable_storage_upgrades";
	
	@Unique
	private static final String PORTABLE_STORAGE_ENABLED_NBT = "portable_storage_enabled";
	
	@Unique
	private static final String PORTABLE_STORAGE_TYPE_NBT = "portable_storage_type";
	
	@Unique
	private long portableStorage$lastHopperCheck = 0;
	
	@Unique
	private static final long HOPPER_CHECK_INTERVAL = 20; // 1秒检查一次

	@Override
	public StorageInventory portableStorage$getInventory() {
		if (portableStorage$inventory == null) {
			portableStorage$inventory = new StorageInventory(54); // 9x6 基础容量
		}
		return portableStorage$inventory;
	}

	@Override
	public void portableStorage$setInventory(StorageInventory inventory) {
		this.portableStorage$inventory = inventory;
	}
	
	@Override
	public UpgradeInventory portableStorage$getUpgradeInventory() {
		if (portableStorage$upgradeInventory == null) {
			portableStorage$upgradeInventory = new UpgradeInventory();
		}
		return portableStorage$upgradeInventory;
	}
	
	@Override
	public void portableStorage$setUpgradeInventory(UpgradeInventory inventory) {
		this.portableStorage$upgradeInventory = inventory;
	}
	
	@Override
	public boolean portableStorage$isStorageEnabled() {
		// 配置不需要条件启用 → 检查本地字段状态
		ServerConfig config = ServerConfig.getInstance();
		if (!config.isRequireConditionToEnable()) {
			// 即使不需要条件启用，也要检查本地字段状态（用于死亡后禁用）
			return portableStorage$enabled;
		}
		// 需要条件启用 → 直接读取随玩家 NBT 同步的本地字段
		return portableStorage$enabled;
	}
	
	@Override
	public void portableStorage$setStorageEnabled(boolean enabled) {
		PlayerEntity self = (PlayerEntity)(Object)this;
		
		portableStorage$enabled = enabled;
		if (self instanceof ServerPlayerEntity serverPlayer) {
			// 兼容：同步服务端的全局状态，便于网络同步逻辑复用
			PlayerEnablementState state =
				PlayerEnablementState.get(serverPlayer.getServer());
			state.setPlayerEnabled(serverPlayer.getUuid(), enabled);
		}
	}
	
	@Override
	public StorageType portableStorage$getStorageType() {
		return portableStorage$storageType;
	}
	
	@Override
	public void portableStorage$setStorageType(StorageType type) {
		this.portableStorage$storageType = type;
	}

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void portableStorage$read(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(PORTABLE_STORAGE_NBT)) {
			StorageInventory inv = new StorageInventory(54);
            // 带注册表上下文的读入（若可用）
            RegistryWrapper.WrapperLookup lookup = null;
            try {
                PlayerEntity self = (PlayerEntity)(Object)this;
                if (self != null && self.getRegistryManager() != null) {
                    lookup = self.getRegistryManager();
                }
            } catch (Throwable ignored) {}
            if (lookup != null) inv.readNbt(nbt.getCompound(PORTABLE_STORAGE_NBT), lookup);
            else inv.readNbt(nbt.getCompound(PORTABLE_STORAGE_NBT));
			this.portableStorage$inventory = inv;
		}
		
		if (nbt.contains(PORTABLE_STORAGE_UPGRADES_NBT)) {
			UpgradeInventory upgrades = new UpgradeInventory();
			upgrades.readNbt(nbt.getCompound(PORTABLE_STORAGE_UPGRADES_NBT));
			this.portableStorage$upgradeInventory = upgrades;
		}
		
		if (nbt.contains(PORTABLE_STORAGE_ENABLED_NBT)) {
			this.portableStorage$enabled = nbt.getBoolean(PORTABLE_STORAGE_ENABLED_NBT);
		} else {
			// 新玩家默认启用（如果配置不需要条件启用）
			ServerConfig config = ServerConfig.getInstance();
			this.portableStorage$enabled = !config.isRequireConditionToEnable();
		}
		
		if (nbt.contains(PORTABLE_STORAGE_TYPE_NBT)) {
			String typeKey = nbt.getString(PORTABLE_STORAGE_TYPE_NBT);
			this.portableStorage$storageType = StorageType.fromKey(typeKey);
		} else {
			// 新玩家默认为完整仓库
			this.portableStorage$storageType = StorageType.FULL;
		}
	}

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void portableStorage$write(NbtCompound nbt, CallbackInfo ci) {
		if (this.portableStorage$inventory != null) {
			NbtCompound out = new NbtCompound();
            // 带注册表上下文的写出（若可用）
            RegistryWrapper.WrapperLookup lookup = null;
            try {
                PlayerEntity self = (PlayerEntity)(Object)this;
                if (self != null && self.getRegistryManager() != null) {
                    lookup = self.getRegistryManager();
                }
            } catch (Throwable ignored) {}
            if (lookup != null) this.portableStorage$inventory.writeNbt(out, lookup);
            else this.portableStorage$inventory.writeNbt(out);
			nbt.put(PORTABLE_STORAGE_NBT, out);
		}
		
		if (this.portableStorage$upgradeInventory != null) {
			NbtCompound out = new NbtCompound();
			this.portableStorage$upgradeInventory.writeNbt(out);
			nbt.put(PORTABLE_STORAGE_UPGRADES_NBT, out);
		}
		
		// 保存启用状态（始终随玩家 NBT 持久化）
		nbt.putBoolean(PORTABLE_STORAGE_ENABLED_NBT, this.portableStorage$enabled);
		
		// 保存仓库类型
		nbt.putString(PORTABLE_STORAGE_TYPE_NBT, this.portableStorage$storageType.getKey());
	}
	
	@Inject(method = "tick", at = @At("TAIL"))
	private void portableStorage$tick(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity)(Object)this;
		World world = self.getWorld();
		
		// 检查是否有漏斗升级
		if (portableStorage$hasHopperUpgrade(self)) {
			long currentTime = world.getTime();
			if (currentTime - portableStorage$lastHopperCheck >= HOPPER_CHECK_INTERVAL) {
				portableStorage$lastHopperCheck = currentTime;
				portableStorage$collectNearbyItems(self, world);
			}
		}
	}
	
	/**
	 * 检查是否有漏斗升级（未禁用）
	 */
	@Unique
	private boolean portableStorage$hasHopperUpgrade(PlayerEntity player) {
		if (portableStorage$upgradeInventory == null) return false;

		for (int i = 0; i < 5; i++) {
			ItemStack stack = portableStorage$upgradeInventory.getStack(i);
			if (!stack.isEmpty() && stack.getItem() == Items.HOPPER && !portableStorage$upgradeInventory.isSlotDisabled(i, (ServerPlayerEntity) player)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 收集周围的掉落物
	 */
	@Unique
	private void portableStorage$collectNearbyItems(PlayerEntity player, World world) {
		// 如果是服务器端玩家，请求同步筛选规则
		if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
			com.portable.storage.storage.FilterRuleManager.requestRulesSync(sp);
		}
		
		// 5格范围的掉落物检测
		Box searchBox = new Box(
			player.getX() - 5, player.getY() - 5, player.getZ() - 5,
			player.getX() + 5, player.getY() + 5, player.getZ() + 5
		);
		
		var itemEntities = world.getEntitiesByClass(ItemEntity.class, searchBox, item -> 
			item.isAlive() && !item.getStack().isEmpty()
		);
		
        for (ItemEntity itemEntity : itemEntities) {
			ItemStack itemStack = itemEntity.getStack();
			if (itemStack.isEmpty()) continue;
			
			// 检查是否为流体桶，如果是则走流体存入逻辑
			if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
				if (UpgradeInventory.isValidFluidItem(itemStack) && !itemStack.isOf(net.minecraft.item.Items.BUCKET)) {
					// 流体桶：转换为空桶并添加流体单位
					String fluidType = UpgradeInventory.getFluidType(itemStack);
					if (fluidType != null) {
						UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(sp);
						
						// 检查是否启用无限流体
						ServerConfig config = ServerConfig.getInstance();
						boolean shouldNotAdd = false;
						if (fluidType.equals("lava") && config.isEnableInfiniteLava()) {
							int units = upgrades.getFluidUnits(fluidType);
							shouldNotAdd = units >= config.getInfiniteLavaThreshold();
						} else if (fluidType.equals("water") && config.isEnableInfiniteWater()) {
							int units = upgrades.getFluidUnits(fluidType);
							shouldNotAdd = units >= config.getInfiniteWaterThreshold();
						}
						
						if (!shouldNotAdd) {
							upgrades.addFluidUnits(fluidType, 1);
						}
						
						// 将空桶存入仓库
						ItemStack emptyBucket = new ItemStack(net.minecraft.item.Items.BUCKET);
						NewStoreService.insertForOnlinePlayer(sp, emptyBucket);
						
						// 移除掉落物
						itemEntity.discard();
						continue;
					}
				}
				
				// 应用筛选和销毁规则
				boolean shouldProcess = false;
				boolean shouldDiscard = false;
				
				if (com.portable.storage.storage.FilterRuleManager.shouldDestroyItem(sp, itemStack)) {
					// 物品匹配销毁规则，直接删除
					// 不存入仓库，直接丢弃
					shouldProcess = true;
					shouldDiscard = true;
				} else if (com.portable.storage.storage.FilterRuleManager.shouldPickupItem(sp, itemStack)) {
					// 物品匹配筛选规则，尝试存入仓库
					// 检查容量限制
					PlayerStorageAccess access = (PlayerStorageAccess) sp;
					StorageType storageType = access.portableStorage$getStorageType();
					if (storageType.hasCapacityLimit()) {
						// 检查是否已达到容量限制
						if (!NewStoreService.canAddNewItemType(sp, itemStack)) {
							// 容量限制，不拾取物品，让物品继续存在
							shouldProcess = false;
							shouldDiscard = false;
						} else {
							// 容量允许，存入仓库
							NewStoreService.insertForOnlinePlayer(sp, itemStack);
							shouldProcess = true;
							shouldDiscard = true;
						}
					} else {
						// 无容量限制，直接存入
						NewStoreService.insertForOnlinePlayer(sp, itemStack);
						shouldProcess = true;
						shouldDiscard = true;
					}
				}
				// 如果既不匹配筛选规则也不匹配销毁规则，则不处理（不拾取）
				
				// 只有被处理的物品才删除掉落物
				if (shouldProcess && shouldDiscard) {
					itemEntity.discard();
				}
			}
		}
	}
}




