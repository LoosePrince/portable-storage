package com.portable.storage.mixin;

import com.portable.storage.config.ServerConfig;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
	private static final String PORTABLE_STORAGE_NBT = "portable_storage";
	
	@Unique
	private static final String PORTABLE_STORAGE_UPGRADES_NBT = "portable_storage_upgrades";
	
	@Unique
	private static final String PORTABLE_STORAGE_ENABLED_NBT = "portable_storage_enabled";
	
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
		PlayerEntity self = (PlayerEntity)(Object)this;
		
		// 检查配置是否需要条件启用
		ServerConfig config = ServerConfig.getInstance();
		if (!config.isRequireConditionToEnable()) {
			// 不需要条件启用，默认启用
			return true;
		}
		
		// 需要条件启用，检查玩家是否已启用
		if (self instanceof ServerPlayerEntity serverPlayer) {
			// 服务端：从持久化状态检查
			com.portable.storage.player.PlayerEnablementState state = 
				com.portable.storage.player.PlayerEnablementState.get(serverPlayer.getServer());
			return state.isPlayerEnabled(serverPlayer.getUuid());
		} else {
			// 客户端：在单人游戏中，服务端和客户端是同一个进程
			// 所以直接使用服务端的持久化状态
			if (self.getWorld().getServer() != null) {
				com.portable.storage.player.PlayerEnablementState state = 
					com.portable.storage.player.PlayerEnablementState.get(self.getWorld().getServer());
				return state.isPlayerEnabled(self.getUuid());
			} else {
				// 纯客户端模式（如服务器列表），使用本地字段
				return portableStorage$enabled;
			}
		}
	}
	
	@Override
	public void portableStorage$setStorageEnabled(boolean enabled) {
		PlayerEntity self = (PlayerEntity)(Object)this;
		
		if (self instanceof ServerPlayerEntity serverPlayer) {
			// 服务端：保存到持久化状态
			com.portable.storage.player.PlayerEnablementState state = 
				com.portable.storage.player.PlayerEnablementState.get(serverPlayer.getServer());
			state.setPlayerEnabled(serverPlayer.getUuid(), enabled);
		} else {
			// 客户端：在单人游戏中，服务端和客户端是同一个进程
			// 所以直接保存到服务端的持久化状态
			if (self.getWorld().getServer() != null) {
				com.portable.storage.player.PlayerEnablementState state = 
					com.portable.storage.player.PlayerEnablementState.get(self.getWorld().getServer());
				state.setPlayerEnabled(self.getUuid(), enabled);
			} else {
				// 纯客户端模式（如服务器列表），保存到本地字段
				portableStorage$enabled = enabled;
			}
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void portableStorage$read(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains(PORTABLE_STORAGE_NBT)) {
			StorageInventory inv = new StorageInventory(54);
			inv.readNbt(nbt.getCompound(PORTABLE_STORAGE_NBT));
			this.portableStorage$inventory = inv;
		}
		
		if (nbt.contains(PORTABLE_STORAGE_UPGRADES_NBT)) {
			UpgradeInventory upgrades = new UpgradeInventory();
			upgrades.readNbt(nbt.getCompound(PORTABLE_STORAGE_UPGRADES_NBT));
			this.portableStorage$upgradeInventory = upgrades;
		}
		
		if (nbt.contains(PORTABLE_STORAGE_ENABLED_NBT)) {
			this.portableStorage$enabled = nbt.getBoolean(PORTABLE_STORAGE_ENABLED_NBT);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void portableStorage$write(NbtCompound nbt, CallbackInfo ci) {
		if (this.portableStorage$inventory != null) {
			NbtCompound out = new NbtCompound();
			this.portableStorage$inventory.writeNbt(out);
			nbt.put(PORTABLE_STORAGE_NBT, out);
		}
		
		if (this.portableStorage$upgradeInventory != null) {
			NbtCompound out = new NbtCompound();
			this.portableStorage$upgradeInventory.writeNbt(out);
			nbt.put(PORTABLE_STORAGE_UPGRADES_NBT, out);
		}
		
		// 保存启用状态（仅客户端需要）
		PlayerEntity self = (PlayerEntity)(Object)this;
		if (!(self instanceof ServerPlayerEntity)) {
			nbt.putBoolean(PORTABLE_STORAGE_ENABLED_NBT, this.portableStorage$enabled);
		}
	}
	
	@Inject(method = "tick", at = @At("TAIL"))
	private void portableStorage$tick(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity)(Object)this;
		World world = self.getWorld();
		
		// 检查是否有漏斗升级
		if (portableStorage$hasHopperUpgrade()) {
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
	private boolean portableStorage$hasHopperUpgrade() {
		if (portableStorage$upgradeInventory == null) return false;

		for (int i = 0; i < 5; i++) {
			ItemStack stack = portableStorage$upgradeInventory.getStack(i);
			if (!stack.isEmpty() && stack.getItem() == Items.HOPPER && !portableStorage$upgradeInventory.isSlotDisabled(i)) {
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
		// 5格范围的掉落物检测
		Box searchBox = new Box(
			player.getX() - 5, player.getY() - 5, player.getZ() - 5,
			player.getX() + 5, player.getY() + 5, player.getZ() + 5
		);
		
		var itemEntities = world.getEntitiesByClass(ItemEntity.class, searchBox, item -> 
			item.isAlive() && !item.getStack().isEmpty()
		);
		
		StorageInventory storage = portableStorage$getInventory();
		
		for (ItemEntity itemEntity : itemEntities) {
			ItemStack itemStack = itemEntity.getStack();
			if (itemStack.isEmpty()) continue;
			
			// 添加到仓库（保留完整组件）
			storage.insertItemStack(itemStack.copy(), world.getTime());
			
			// 移除掉落物
			itemEntity.discard();
		}
	}
}


