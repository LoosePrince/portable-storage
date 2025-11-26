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
import com.portable.storage.storage.AutoEatMode;
import com.portable.storage.storage.FilterRuleManager;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.StorageType;
import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.net.payload.SyncFilterRulesC2SPayload;
import com.portable.storage.world.SpaceRiftManager;

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
	private static final String PORTABLE_STORAGE_AUTO_EAT_MODE_NBT = "portable_storage_auto_eat_mode";
	
	@Unique
	private static final String PORTABLE_STORAGE_FILTER_RULES_NBT = "portable_storage_filter_rules";
	
	@Unique
	private static final String PORTABLE_STORAGE_DESTROY_RULES_NBT = "portable_storage_destroy_rules";
	
	@Unique
	private static final String PORTABLE_STORAGE_AUTO_EAT_RULES_NBT = "portable_storage_auto_eat_rules";
	
	@Unique
	private static final String PORTABLE_STORAGE_RIFT_INITIALIZED_NBT = "portable_storage_rift_initialized";
	
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
		PlayerEntity self = (PlayerEntity)(Object)this;
		
		if (nbt.contains(PORTABLE_STORAGE_NBT)) {
			StorageInventory inv = new StorageInventory(54);
            // 带注册表上下文的读入（若可用）
            RegistryWrapper.WrapperLookup lookup = null;
            try {
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
		
		ServerConfig config = ServerConfig.getInstance();
		
		if (nbt.contains(PORTABLE_STORAGE_ENABLED_NBT)) {
			this.portableStorage$enabled = nbt.getBoolean(PORTABLE_STORAGE_ENABLED_NBT);
		} else {
			// 新玩家默认启用（如果配置不需要条件启用）
			this.portableStorage$enabled = !config.isRequireConditionToEnable();
		}
		
		if (nbt.contains(PORTABLE_STORAGE_TYPE_NBT)) {
			String typeKey = nbt.getString(PORTABLE_STORAGE_TYPE_NBT);
			this.portableStorage$storageType = StorageType.fromKey(typeKey);
		} else {
			// 新玩家默认为完整仓库
			this.portableStorage$storageType = StorageType.FULL;
		}
		
		// 如果配置不需要条件启用，强制所有玩家使用完整仓库
		if (!config.isRequireConditionToEnable()) {
			// 如果之前是初级仓库，自动升级为完整仓库
			if (this.portableStorage$storageType == StorageType.PRIMARY) {
				this.portableStorage$storageType = StorageType.FULL;
			}
		}
		
		// 读取并恢复玩家状态
		if (self instanceof ServerPlayerEntity serverPlayer) {
			// 读取并恢复裂隙初始化状态
			if (nbt.contains(PORTABLE_STORAGE_RIFT_INITIALIZED_NBT)) {
				SpaceRiftManager.setPlotInitialized(serverPlayer.getUuid(), nbt.getBoolean(PORTABLE_STORAGE_RIFT_INITIALIZED_NBT));
			}
			// 读取并恢复进食模式
			if (nbt.contains(PORTABLE_STORAGE_AUTO_EAT_MODE_NBT)) {
				int modeIndex = nbt.getInt(PORTABLE_STORAGE_AUTO_EAT_MODE_NBT);
				AutoEatMode mode = AutoEatMode.fromIndex(modeIndex);
				ServerNetworkingHandlers.setPlayerAutoEatMode(serverPlayer, mode);
			}
			
			// 读取并恢复筛选规则
			java.util.List<SyncFilterRulesC2SPayload.FilterRule> filterRules = new java.util.ArrayList<>();
			java.util.List<SyncFilterRulesC2SPayload.FilterRule> destroyRules = new java.util.ArrayList<>();
			java.util.List<SyncFilterRulesC2SPayload.FilterRule> autoEatRules = new java.util.ArrayList<>();
			
			if (nbt.contains(PORTABLE_STORAGE_FILTER_RULES_NBT)) {
				NbtCompound filterRulesNbt = nbt.getCompound(PORTABLE_STORAGE_FILTER_RULES_NBT);
				int count = filterRulesNbt.getInt("count");
				for (int i = 0; i < count; i++) {
					if (filterRulesNbt.contains("rule_" + i)) {
						NbtCompound ruleNbt = filterRulesNbt.getCompound("rule_" + i);
						filterRules.add(new SyncFilterRulesC2SPayload.FilterRule(
							ruleNbt.getString("matchRule"),
							ruleNbt.getBoolean("isWhitelist"),
							ruleNbt.getBoolean("enabled")
						));
					}
				}
			}
			
			if (nbt.contains(PORTABLE_STORAGE_DESTROY_RULES_NBT)) {
				NbtCompound destroyRulesNbt = nbt.getCompound(PORTABLE_STORAGE_DESTROY_RULES_NBT);
				int count = destroyRulesNbt.getInt("count");
				for (int i = 0; i < count; i++) {
					if (destroyRulesNbt.contains("rule_" + i)) {
						NbtCompound ruleNbt = destroyRulesNbt.getCompound("rule_" + i);
						destroyRules.add(new SyncFilterRulesC2SPayload.FilterRule(
							ruleNbt.getString("matchRule"),
							ruleNbt.getBoolean("isWhitelist"),
							ruleNbt.getBoolean("enabled")
						));
					}
				}
			}
			
			if (nbt.contains(PORTABLE_STORAGE_AUTO_EAT_RULES_NBT)) {
				NbtCompound autoEatRulesNbt = nbt.getCompound(PORTABLE_STORAGE_AUTO_EAT_RULES_NBT);
				int count = autoEatRulesNbt.getInt("count");
				for (int i = 0; i < count; i++) {
					if (autoEatRulesNbt.contains("rule_" + i)) {
						NbtCompound ruleNbt = autoEatRulesNbt.getCompound("rule_" + i);
						autoEatRules.add(new SyncFilterRulesC2SPayload.FilterRule(
							ruleNbt.getString("matchRule"),
							ruleNbt.getBoolean("isWhitelist"),
							ruleNbt.getBoolean("enabled")
						));
					}
				}
			} else if (!filterRules.isEmpty()) {
				autoEatRules.addAll(filterRules);
			}
			
			// 恢复筛选规则到内存
			if (!filterRules.isEmpty() || !destroyRules.isEmpty() || !autoEatRules.isEmpty()) {
				FilterRuleManager.syncPlayerRules(serverPlayer, filterRules, destroyRules, autoEatRules);
			}
			
			// 如果配置不需要条件启用，确保仓库始终启用
			if (!ServerConfig.getInstance().isRequireConditionToEnable()) {
				portableStorage$enabled = true;
				PlayerEnablementState.get(serverPlayer.getServer()).setPlayerEnabled(serverPlayer.getUuid(), true);
			}
		} else {
			if (!ServerConfig.getInstance().isRequireConditionToEnable()) {
				portableStorage$enabled = true;
			}
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
		
		// 保存玩家状态
		PlayerEntity self = (PlayerEntity)(Object)this;
		if (self instanceof ServerPlayerEntity serverPlayer) {
			AutoEatMode mode = ServerNetworkingHandlers.getPlayerAutoEatMode(serverPlayer);
			nbt.putInt(PORTABLE_STORAGE_AUTO_EAT_MODE_NBT, mode.getIndex());
			nbt.putBoolean(PORTABLE_STORAGE_RIFT_INITIALIZED_NBT, SpaceRiftManager.isPlotInitialized(serverPlayer.getUuid()));
			
			// 保存筛选规则
			FilterRuleManager.PlayerFilterRules rules = FilterRuleManager.getPlayerRules(serverPlayer);
			if (rules != null) {
				// 保存筛选规则
				if (!rules.filterRules.isEmpty()) {
					NbtCompound filterRulesNbt = new NbtCompound();
					filterRulesNbt.putInt("count", rules.filterRules.size());
					for (int i = 0; i < rules.filterRules.size(); i++) {
						SyncFilterRulesC2SPayload.FilterRule rule = rules.filterRules.get(i);
						NbtCompound ruleNbt = new NbtCompound();
						ruleNbt.putString("matchRule", rule.matchRule);
						ruleNbt.putBoolean("isWhitelist", rule.isWhitelist);
						ruleNbt.putBoolean("enabled", rule.enabled);
						filterRulesNbt.put("rule_" + i, ruleNbt);
					}
					nbt.put(PORTABLE_STORAGE_FILTER_RULES_NBT, filterRulesNbt);
				}
				
				// 保存销毁规则
				if (!rules.destroyRules.isEmpty()) {
					NbtCompound destroyRulesNbt = new NbtCompound();
					destroyRulesNbt.putInt("count", rules.destroyRules.size());
					for (int i = 0; i < rules.destroyRules.size(); i++) {
						SyncFilterRulesC2SPayload.FilterRule rule = rules.destroyRules.get(i);
						NbtCompound ruleNbt = new NbtCompound();
						ruleNbt.putString("matchRule", rule.matchRule);
						ruleNbt.putBoolean("isWhitelist", rule.isWhitelist);
						ruleNbt.putBoolean("enabled", rule.enabled);
						destroyRulesNbt.put("rule_" + i, ruleNbt);
					}
					nbt.put(PORTABLE_STORAGE_DESTROY_RULES_NBT, destroyRulesNbt);
				}
				
				// 保存自动喂食规则
				if (!rules.autoEatRules.isEmpty()) {
					NbtCompound autoEatRulesNbt = new NbtCompound();
					autoEatRulesNbt.putInt("count", rules.autoEatRules.size());
					for (int i = 0; i < rules.autoEatRules.size(); i++) {
						SyncFilterRulesC2SPayload.FilterRule rule = rules.autoEatRules.get(i);
						NbtCompound ruleNbt = new NbtCompound();
						ruleNbt.putString("matchRule", rule.matchRule);
						ruleNbt.putBoolean("isWhitelist", rule.isWhitelist);
						ruleNbt.putBoolean("enabled", rule.enabled);
						autoEatRulesNbt.put("rule_" + i, ruleNbt);
					}
					nbt.put(PORTABLE_STORAGE_AUTO_EAT_RULES_NBT, autoEatRulesNbt);
				}
			}
		}
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
	 * 检查是否有漏斗升级（未禁用且仓库已启用）
	 */
	@Unique
	private boolean portableStorage$hasHopperUpgrade(PlayerEntity player) {
		if (portableStorage$upgradeInventory == null) return false;
		
		// 检查仓库是否启用
		PlayerStorageAccess access = (PlayerStorageAccess) player;
		if (!access.portableStorage$isStorageEnabled()) {
			return false;
		}

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
						
                        // 将空桶存入仓库（返回未接收的部分）
                        ItemStack emptyBucket = new ItemStack(net.minecraft.item.Items.BUCKET);
                        long accepted = NewStoreService.insertCountForOnlinePlayer(sp, emptyBucket);
                        if (accepted <= 0) {
                            // 未被接受则把空桶掉落在地（保持物品不丢失）
                            sp.dropItem(emptyBucket, false);
                        }
						
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
                            // 容量允许，存入仓库（仅接受一部分时，保留剩余）
                            long accepted = NewStoreService.insertCountForOnlinePlayer(sp, itemStack);
                            if (accepted > 0) {
                                shouldProcess = true;
                                int cnt = itemStack.getCount();
                                if (accepted >= cnt) {
                                    shouldDiscard = true; // 全部接收，移除掉落物
                                } else {
                                    // 部分接收，更新掉落物剩余数量
                                    itemStack.setCount(cnt - (int)Math.min(Integer.MAX_VALUE, accepted));
                                    shouldDiscard = false;
                                }
                            }
						}
					} else {
                        // 无容量限制，直接存入（部分接收也要保留剩余）
                        long accepted = NewStoreService.insertCountForOnlinePlayer(sp, itemStack);
                        if (accepted > 0) {
                            shouldProcess = true;
                            int cnt = itemStack.getCount();
                            if (accepted >= cnt) {
                                shouldDiscard = true;
                            } else {
                                itemStack.setCount(cnt - (int)Math.min(Integer.MAX_VALUE, accepted));
                                shouldDiscard = false;
                            }
                        }
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




