package com.portable.storage.net;

import com.portable.storage.PortableStorage;
import com.portable.storage.block.ModBlocks;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.crafting.OverlayCraftingManager;
import com.portable.storage.net.payload.ConfigSyncS2CPayload;
import com.portable.storage.net.payload.CraftingOverlayActionC2SPayload;
import com.portable.storage.net.payload.FluidClickC2SPayload;
import com.portable.storage.net.payload.FluidConversionC2SPayload;
import com.portable.storage.net.payload.FluidSlotClickC2SPayload;
import com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload;
import com.portable.storage.net.payload.OverlayCraftingSyncS2CPayload;
import com.portable.storage.net.payload.RequestOpenScreenC2SPayload;
import com.portable.storage.net.payload.StorageActionC2SPayload;
import com.portable.storage.net.payload.StorageSyncS2CPayload;
import com.portable.storage.net.payload.SyncControlC2SPayload;
import com.portable.storage.net.payload.XpBottleClickC2SPayload;
import com.portable.storage.net.payload.XpBottleConversionC2SPayload;
import com.portable.storage.net.payload.XpBottleMaintenanceToggleC2SPayload;
import com.portable.storage.net.payload.SyncFilterRulesC2SPayload;
import com.portable.storage.net.payload.SyncBarrelFilterRulesC2SPayload;
import com.portable.storage.newstore.NewStoreService;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.player.StoragePersistence;
import com.portable.storage.screen.PortableCraftingScreenHandler;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.StorageType;
import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.storage.AutoEatMode;
import com.portable.storage.sync.PlayerViewState;
import com.portable.storage.sync.StorageSyncManager;
import com.portable.storage.world.SpaceRiftManager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ServerNetworkingHandlers {
	private ServerNetworkingHandlers() {}
    // 附魔之瓶升级：循环的存取等级索引，简单存内存，不做持久化
    private static final java.util.Map<java.util.UUID, Integer> xpStepIndexByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int[] XP_STEPS = new int[] {1, 5, 10, 100};
    
    // 附魔金苹果升级：自动进食模式，简单存内存，不做持久化
    private static final java.util.Map<java.util.UUID, AutoEatMode> autoEatModeByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * 获取玩家的当前自动进食模式
     */
    public static AutoEatMode getPlayerAutoEatMode(ServerPlayerEntity player) {
        return autoEatModeByPlayer.getOrDefault(player.getUuid(), AutoEatMode.DEFAULT);
    }
	
	/**
	 * 检查玩家是否已启用随身仓库功能
	 */
	private static boolean isPlayerStorageEnabled(ServerPlayerEntity player) {
		PlayerStorageAccess access = (PlayerStorageAccess) player;
		return access.portableStorage$isStorageEnabled();
	}
	
	/**
	 * 检查并拒绝未启用玩家的请求
	 */
	private static boolean checkAndRejectIfNotEnabled(ServerPlayerEntity player) {
		if (!isPlayerStorageEnabled(player)) {
			return true; // 拒绝请求
		}
		return false; // 允许请求
	}

	public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SyncControlC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
                if (payload.op() == SyncControlC2SPayload.Op.REQUEST) {
                    // 标记玩家开始查看仓库界面
                    PlayerViewState.startViewing(player.getUuid());
                    // 直接发送启用状态与全量同步
                    sendEnablementSync(player);
                    if (!checkAndRejectIfNotEnabled(player)) {
                        sendSync(player);
                    }
                } else if (payload.op() == SyncControlC2SPayload.Op.ACK) {
                    StorageSyncManager.handleSyncAck(player.getUuid(), payload.syncId(), payload.success());
                }
			});
		});

		// 新统一动作包：服务端集中处理
		ServerPlayNetworking.registerGlobalReceiver(StorageActionC2SPayload.ID, (payload, context) -> {
			final StorageActionC2SPayload p = payload;
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				switch (p.target()) {
					case STORAGE -> handleStorageAction(player, p);
					case UPGRADE -> handleUpgradeAction(player, p);
					case FLUID -> handleFluidAction(player, p);
					case XP_BOTTLE -> handleXpBottleAction(player, p);
					case SLOT -> handleSlotDepositAction(player, p);
					case TRASH -> handleTrashAction(player, p);
				}
			});
		});

		// 兼容：保留旧接收器直到完全移除旧类





        // 统一覆盖式合成/配方填充
        ServerPlayNetworking.registerGlobalReceiver(CraftingOverlayActionC2SPayload.ID, (payload, context) -> {
            final CraftingOverlayActionC2SPayload p = payload;
            context.server().execute(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.player();
                if (checkAndRejectIfNotEnabled(player)) return;
                switch (p.action()) {
                    case CLICK -> {
                        OverlayCraftingManager.handleClick(player, p.slotIndex(), p.button(), p.shift());
                        OverlayCraftingManager.State st = OverlayCraftingManager.get(player);
                        net.minecraft.item.ItemStack[] copy = new net.minecraft.item.ItemStack[st.slots.length];
                        for (int i = 0; i < st.slots.length; i++) copy[i] = st.slots[i].copy();
                        ServerPlayNetworking.send(player, new OverlayCraftingSyncS2CPayload(copy));
                    }
                    case DOUBLE_CLICK -> {
                        var st = OverlayCraftingManager.get(player);
                        net.minecraft.item.ItemStack cursor = player.currentScreenHandler.getCursorStack();
                        if (cursor.isEmpty()) return;
                        int maxPer = Math.min(cursor.getMaxCount(), player.getInventory().getMaxCountPerStack());
                        for (int i = 1; i <= 9 && cursor.getCount() < maxPer; i++) {
                            net.minecraft.item.ItemStack s = st.slots[i];
                            if (!s.isEmpty() && net.minecraft.item.ItemStack.areItemsAndComponentsEqual(s, cursor)) {
                                int can = Math.min(maxPer - cursor.getCount(), s.getCount());
                                if (can > 0) {
                                    cursor.increment(can);
                                    s.decrement(can);
                                    if (s.isEmpty()) st.slots[i] = net.minecraft.item.ItemStack.EMPTY;
                                }
                            }
                        }
                        player.currentScreenHandler.setCursorStack(cursor);
                        OverlayCraftingManager.updateResult(player);
                        net.minecraft.item.ItemStack[] copy = new net.minecraft.item.ItemStack[st.slots.length];
                        for (int i = 0; i < st.slots.length; i++) copy[i] = st.slots[i].copy();
                        ServerPlayNetworking.send(player, new OverlayCraftingSyncS2CPayload(copy));
                    }
                    case REFILL -> refillCraftingFromStorage(player, p.slotIndex(), p.targetStack());
                    case EMI_FILL -> handleEmiRecipeFill(player, p.recipeId(), p.slotIndices(), p.itemCounts());
                    case REFUND -> {
                        OverlayCraftingManager.refundAll(player);
                        var handler = player.currentScreenHandler;
                        if (handler instanceof net.minecraft.screen.PlayerScreenHandler playerHandler) {
                            for (int i = 1; i <= 4 && i < playerHandler.slots.size(); i++) {
                                net.minecraft.screen.slot.Slot slot = playerHandler.getSlot(i);
                                net.minecraft.item.ItemStack stack = slot.getStack();
                                if (!stack.isEmpty()) {
                                    if (!player.getInventory().insertStack(stack)) {
                                        player.dropItem(stack, false);
                                    }
                                    slot.setStack(net.minecraft.item.ItemStack.EMPTY);
                                    slot.markDirty();
                                }
                            }
                            net.minecraft.screen.slot.Slot outputSlot = playerHandler.getSlot(0);
                            if (!outputSlot.getStack().isEmpty()) {
                                net.minecraft.item.ItemStack outputStack = outputSlot.getStack();
                                if (!player.getInventory().insertStack(outputStack)) {
                                    player.dropItem(outputStack, false);
                                }
                                outputSlot.setStack(net.minecraft.item.ItemStack.EMPTY);
                                outputSlot.markDirty();
                            }
                        }
                        handler.sendContentUpdates();
                        sendSync(player);
                    }
                }
            });
        });




        // 旧 RefillCraftingC2SPayload 接收器已移除（使用统一 CraftingOverlayActionC2SPayload）

		// 流体槽位点击
		ServerPlayNetworking.registerGlobalReceiver(FluidSlotClickC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				
				ItemStack cursor = player.currentScreenHandler.getCursorStack();
				ItemStack fluidStack = upgrades.getFluidStack();
				
				if (cursor.isEmpty()) {
					// 手持空，取出流体槽位物品
					if (!fluidStack.isEmpty()) {
						upgrades.setFluidStack(ItemStack.EMPTY);
                        player.currentScreenHandler.setCursorStack(fluidStack);
                        player.currentScreenHandler.sendContentUpdates();
                        sendSync(player);
					}
				} else {
					// 手持物品，检查是否有效
					if (UpgradeInventory.isValidFluidItem(cursor)) {
						if (cursor.isOf(net.minecraft.item.Items.BUCKET)) {
							// 空桶：检查是否有流体可以转换
							boolean hasFluid = false;
							for (String fluidType : new String[]{"lava", "water", "milk"}) {
								if (upgrades.getFluidUnits(fluidType) > 0) {
									// 转换为流体桶并减少流体单位
									upgrades.removeFluidUnits(fluidType, 1);
									player.currentScreenHandler.setCursorStack(UpgradeInventory.createFluidBucket(fluidType));
									hasFluid = true;
									break;
								}
							}
							if (!hasFluid) {
								// 没有流体，正常交换
								upgrades.setFluidStack(cursor.copy());
								player.currentScreenHandler.setCursorStack(fluidStack);
							}
						} else {
							// 流体桶：转换为空桶并添加流体单位
							String fluidType = UpgradeInventory.getFluidType(cursor);
							if (fluidType != null) {
								upgrades.addFluidUnits(fluidType, 1);
								player.currentScreenHandler.setCursorStack(new ItemStack(net.minecraft.item.Items.BUCKET));
							} else {
								// 其他有效物品，正常交换
								upgrades.setFluidStack(cursor.copy());
								player.currentScreenHandler.setCursorStack(fluidStack);
							}
						}
						player.currentScreenHandler.sendContentUpdates();
						sendSync(player);
					}
				}
			});
		});
		
		// 虚拟流体点击
		ServerPlayNetworking.registerGlobalReceiver(FluidClickC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				
				String fluidType = payload.fluidType();
				int button = payload.button();
				
				// 左键点击虚拟流体：不处理
				if (button == 0) {
					return;
				}
				// 右键：存入一个单位的流体（从流体桶转换）
				else if (button == 1) {
					ItemStack cursor = player.currentScreenHandler.getCursorStack();
					if (!cursor.isEmpty() && cursor.isOf(UpgradeInventory.createFluidBucket(fluidType).getItem())) {
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
                        player.currentScreenHandler.setCursorStack(new ItemStack(net.minecraft.item.Items.BUCKET));
                        player.currentScreenHandler.sendContentUpdates();
                        sendSync(player);
					}
				}
			});
		});
		
		// 虚拟流体转换
		ServerPlayNetworking.registerGlobalReceiver(FluidConversionC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				
				String fluidType = payload.fluidType();
				int button = payload.button();
				
				// 右键：空桶转换为流体桶
				if (button == 1) {
					ItemStack cursor = player.currentScreenHandler.getCursorStack();
					if (!cursor.isEmpty() && cursor.isOf(net.minecraft.item.Items.BUCKET)) {
						int units = upgrades.getFluidUnits(fluidType);
						if (units > 0) {
							// 检查是否启用无限流体
							ServerConfig config = ServerConfig.getInstance();
							boolean shouldNotConsume = false;
							if (fluidType.equals("lava") && config.isEnableInfiniteLava()) {
								shouldNotConsume = units >= config.getInfiniteLavaThreshold();
							} else if (fluidType.equals("water") && config.isEnableInfiniteWater()) {
								shouldNotConsume = units >= config.getInfiniteWaterThreshold();
							}
							
							if (!shouldNotConsume) {
								upgrades.removeFluidUnits(fluidType, 1);
							}
							ItemStack fluidBucket = UpgradeInventory.createFluidBucket(fluidType);
							
							// 如果手中有多个空桶，将多余的空桶放入仓库
							if (cursor.getCount() > 1) {
								ItemStack remainingBuckets = cursor.copy();
								remainingBuckets.decrement(1); // 减少一个空桶用于转换
								
								// 将剩余的空桶放入仓库
                                // 新版：将多余空桶存入分离存储
                                insertIntoNewStorage(player, remainingBuckets);
								
								// 给玩家一个流体桶
								player.currentScreenHandler.setCursorStack(fluidBucket);
							} else {
								// 只有一个空桶，直接转换
								player.currentScreenHandler.setCursorStack(fluidBucket);
							}
							
                            player.currentScreenHandler.sendContentUpdates();
                            sendSync(player);
						}
					}
				}
				// 左键：从背包中消耗桶取出流体桶
				else if (button == 0) {
					int units = upgrades.getFluidUnits(fluidType);
					if (units > 0) {
						// 在背包中查找空桶
						ItemStack bucketToConsume = null;
						int bucketSlot = -1;
						
						var handler = player.currentScreenHandler;
						for (int i = 0; i < handler.slots.size(); i++) {
							var slot = handler.slots.get(i);
							if (slot.hasStack() && slot.getStack().isOf(net.minecraft.item.Items.BUCKET)) {
								bucketToConsume = slot.getStack();
								bucketSlot = i;
								break;
							}
						}
						
						if (bucketToConsume != null) {
							// 检查是否启用无限流体
							ServerConfig config = ServerConfig.getInstance();
							boolean shouldNotConsume = false;
							if (fluidType.equals("lava") && config.isEnableInfiniteLava()) {
								shouldNotConsume = units >= config.getInfiniteLavaThreshold();
							} else if (fluidType.equals("water") && config.isEnableInfiniteWater()) {
								shouldNotConsume = units >= config.getInfiniteWaterThreshold();
							}
							
							// 消耗一个桶
							bucketToConsume.decrement(1);
							if (bucketToConsume.isEmpty()) {
								handler.slots.get(bucketSlot).setStack(ItemStack.EMPTY);
							}
							
							// 消耗一个流体单位并创建流体桶（如果未启用无限流体或数量未达到阈值）
							if (!shouldNotConsume) {
								upgrades.removeFluidUnits(fluidType, 1);
							}
							ItemStack fluidBucket = UpgradeInventory.createFluidBucket(fluidType);
							
							// 将流体桶放入背包
							insertIntoPlayerInventory(player, fluidBucket);
							
                            player.currentScreenHandler.sendContentUpdates();
                            sendSync(player);
						}
					}
				}
			});
		});
		
        // 旧 RefundCraftingSlotsC2SPayload 接收器已移除（使用统一 CraftingOverlayActionC2SPayload）
		
		// 旧的升级槽位点击处理已移除，现在使用统一的StorageActionC2SPayload
		
        // 旧 EmiRecipeFillC2SPayload 接收器已移除（使用统一 CraftingOverlayActionC2SPayload）
		
		// 筛选规则同步
		ServerPlayNetworking.registerGlobalReceiver(SyncFilterRulesC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				com.portable.storage.storage.FilterRuleManager.syncPlayerRules(player, payload.filterRules(), payload.destroyRules());
			});
		});
		
		// 处理绑定木桶筛选规则同步
		ServerPlayNetworking.registerGlobalReceiver(SyncBarrelFilterRulesC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				net.minecraft.world.World world = player.getWorld();
				net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(payload.barrelPos());
				
				if (blockEntity instanceof com.portable.storage.blockentity.BoundBarrelBlockEntity barrel) {
					// 转换规则格式
					java.util.List<com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule> filterRules = new java.util.ArrayList<>();
					for (com.portable.storage.net.payload.SyncBarrelFilterRulesC2SPayload.FilterRule rule : payload.filterRules()) {
						filterRules.add(new com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule(
							rule.matchRule(), rule.isWhitelist(), rule.enabled()
						));
					}
					
					// 更新绑定木桶的规则
					barrel.setFilterRules(filterRules);
					barrel.markDirty();
					
					// 调试信息
					System.out.println("服务器更新绑定木桶规则: 筛选=" + filterRules.size());
				}
			});
		});

		// 玩家加入时发送容器显示配置同步（统一配置同步）
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			if (player != null) {
				ServerConfig config = ServerConfig.getInstance();
				NbtCompound data = new NbtCompound();
				data.putBoolean("stonecutter", config.isStonecutter());
				data.putBoolean("cartographyTable", config.isCartographyTable());
				data.putBoolean("smithingTable", config.isSmithingTable());
				data.putBoolean("grindstone", config.isGrindstone());
				data.putBoolean("loom", config.isLoom());
				data.putBoolean("furnace", config.isFurnace());
				data.putBoolean("smoker", config.isSmoker());
				data.putBoolean("blastFurnace", config.isBlastFurnace());
				data.putBoolean("anvil", config.isAnvil());
				data.putBoolean("enchantingTable", config.isEnchantingTable());
				data.putBoolean("brewingStand", config.isBrewingStand());
				data.putBoolean("beacon", config.isBeacon());
				data.putBoolean("chest", config.isChest());
				data.putBoolean("barrel", config.isBarrel());
				data.putBoolean("enderChest", config.isEnderChest());
				data.putBoolean("shulkerBox", config.isShulkerBox());
				data.putBoolean("dispenser", config.isDispenser());
				data.putBoolean("dropper", config.isDropper());
				data.putBoolean("crafter", config.isCrafter());
				data.putBoolean("hopper", config.isHopper());
				data.putBoolean("trappedChest", config.isTrappedChest());
				data.putBoolean("hopperMinecart", config.isHopperMinecart());
				data.putBoolean("chestMinecart", config.isChestMinecart());
				data.putBoolean("chestBoat", config.isChestBoat());
				data.putBoolean("bambooChestRaft", config.isBambooChestRaft());
				ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
					ConfigSyncS2CPayload.Topic.DISPLAY_CONFIG, data
				));
				
				// 发送裂隙配置同步
				NbtCompound riftData = new NbtCompound();
				riftData.putString("riftUpgradeItem", config.getRiftUpgradeItem());
				riftData.putInt("riftSize", config.getRiftSize());
				ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
					ConfigSyncS2CPayload.Topic.RIFT_CONFIG, riftData
				));
				
				// 发送虚拟合成配置同步
				NbtCompound virtualCraftingData = new NbtCompound();
				virtualCraftingData.putBoolean("enableVirtualCrafting", config.isEnableVirtualCrafting());
				ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
					ConfigSyncS2CPayload.Topic.VIRTUAL_CRAFTING_CONFIG, virtualCraftingData
				));
			}
		});

		// 虚拟"瓶装经验"点击：button=0 左键取出，1 右键存入
		ServerPlayNetworking.registerGlobalReceiver(XpBottleClickC2SPayload.ID, (payload, context) -> {
			int button = payload.button();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				// 必须有附魔之瓶升级且未禁用
				if (upgrades.isSlotDisabled(7, player) || upgrades.getStack(7).isEmpty()) return;
				
				// 检查是否启用了等级维持，如果启用则拒绝手动存取
				if (upgrades.isLevelMaintenanceEnabled()) {
					player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.maintenance_blocked"), true);
					return;
				}
				
				int idx = xpStepIndexByPlayer.getOrDefault(player.getUuid(), 0);
				int levels = XP_STEPS[idx];
				if (button == 0) {
					// 左键：从"仓库XP池"取出经验，增加给玩家
					// 先计算需要取出的经验值
					int xpNeeded = xpForLevels(player.experienceLevel, levels);
					long availableXp = upgrades.getXpPool();
					
					if (availableXp >= xpNeeded) {
						// 经验足够，按等级提升
						long taken = upgrades.removeFromXpPool(xpNeeded);
						if (taken > 0) {
							int actualWithdrawn = withdrawPlayerXpByLevels(player, levels);
							player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.delta", "+" + actualWithdrawn), true);
						}
					} else if (availableXp > 0) {
						// 经验不足，取出全部剩余经验
						long taken = upgrades.removeFromXpPool(availableXp);
						if (taken > 0) {
							// 直接增加经验值，不按等级提升
							player.addExperience((int)Math.min(Integer.MAX_VALUE, taken));
							player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.delta", "+" + taken), true);
						}
					}
					sendUpgradeSync(player);
				} else {
					// 右键：从玩家扣除经验，存入"仓库XP池"
					// 使用新的方法扣除玩家经验，避免残留
					int deposited = depositPlayerXpByLevels(player, levels);
					if (deposited > 0) {
						upgrades.addToXpPool(deposited);
						player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.delta", "-" + deposited), true);
					}
					sendUpgradeSync(player);
				}
			});
		});

		// 附魔之瓶等级维持切换
		ServerPlayNetworking.registerGlobalReceiver(XpBottleMaintenanceToggleC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				// 必须有附魔之瓶升级且未禁用
				if (upgrades.isSlotDisabled(7, player) || upgrades.getStack(7).isEmpty()) return;
				
				// 切换等级维持状态
				upgrades.toggleLevelMaintenance();
				boolean enabled = upgrades.isLevelMaintenanceEnabled();
				Text status = enabled ? 
					Text.translatable(PortableStorage.MOD_ID + ".toggle.enabled") : 
					Text.translatable(PortableStorage.MOD_ID + ".toggle.disabled");
				player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.maintenance_toggle", status), true);
				sendUpgradeSync(player);
			});
		});

		// 附魔之瓶转换：玻璃瓶右键瓶装经验转换为附魔之瓶
		ServerPlayNetworking.registerGlobalReceiver(XpBottleConversionC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				// 必须有附魔之瓶升级且未禁用
				if (upgrades.isSlotDisabled(7, player) || upgrades.getStack(7).isEmpty()) return;
				
				// 检查玩家是否拿着玻璃瓶
				ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
				if (cursorStack.isEmpty() || !cursorStack.isOf(net.minecraft.item.Items.GLASS_BOTTLE)) {
					player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.conversion.no_bottle"), true);
					return;
				}
				
				int bottleCount = cursorStack.getCount();
				long availableXp = upgrades.getXpPool();
				
				// 计算可以转换的附魔之瓶数量（每11点经验=1个附魔之瓶）
				int maxConvertible = (int) Math.min(bottleCount, availableXp / 11);
				
				if (maxConvertible <= 0) {
					player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.conversion.insufficient_xp"), true);
					return;
				}
				
				// 扣除经验值
				long xpUsed = maxConvertible * 11L;
				upgrades.removeFromXpPool(xpUsed);
				
				// 创建附魔之瓶
				ItemStack experienceBottles = new ItemStack(net.minecraft.item.Items.EXPERIENCE_BOTTLE, maxConvertible);
				
				// 处理剩余的玻璃瓶
				int remainingBottles = bottleCount - maxConvertible;
				if (remainingBottles > 0) {
					// 将剩余玻璃瓶存入仓库
					// 新版：存入分离存储
					ItemStack remainingBottleStack = new ItemStack(net.minecraft.item.Items.GLASS_BOTTLE, remainingBottles);
					insertIntoNewStorage(player, remainingBottleStack);
					player.currentScreenHandler.setCursorStack(experienceBottles);
					player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.conversion.partial", maxConvertible, remainingBottles), true);
				} else {
					// 全部转换
					player.currentScreenHandler.setCursorStack(experienceBottles);
					player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.conversion.complete", maxConvertible), true);
				}
				
				// 同步数据
				sendUpgradeSync(player);
				sendSync(player);
			});
		});

        // 统一打开界面：根据 screen 枚举打开对应界面
        ServerPlayNetworking.registerGlobalReceiver(RequestOpenScreenC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.player();
                if (payload.screen() == RequestOpenScreenC2SPayload.Screen.VANILLA_CRAFTING) {
                    // 切回原版工作台
                    final net.minecraft.util.math.BlockPos[] openPosHolder = new net.minecraft.util.math.BlockPos[1];
                    final net.minecraft.world.World[] openWorldHolder = new net.minecraft.world.World[1];
                    if (player.currentScreenHandler instanceof PortableCraftingScreenHandler pch) {
                        for (int i = 1; i <= 9 && i < pch.slots.size(); i++) {
                            net.minecraft.screen.slot.Slot slot = pch.getSlot(i);
                            ItemStack st = slot.getStack();
                            if (!st.isEmpty()) {
                                ItemStack copy = st.copy();
                                slot.setStack(ItemStack.EMPTY);
                                insertIntoPlayerInventory(player, copy);
                            }
                        }
                        pch.getContext().run((w, pos) -> {
                            openWorldHolder[0] = w;
                            openPosHolder[0] = pos;
                        });
                    }
                    player.closeHandledScreen();
                    player.openHandledScreen(new net.minecraft.screen.NamedScreenHandlerFactory() {
                        @Override
                        public net.minecraft.text.Text getDisplayName() {
                            return net.minecraft.text.Text.translatable("container.crafting");
                        }
                        @Override
                        public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, net.minecraft.entity.player.PlayerEntity playerEntity) {
                            net.minecraft.world.World w = openWorldHolder[0] != null ? openWorldHolder[0] : player.getWorld();
                            net.minecraft.util.math.BlockPos p = openPosHolder[0] != null ? openPosHolder[0] : player.getBlockPos();
                            return new net.minecraft.screen.CraftingScreenHandler(syncId, inv, net.minecraft.screen.ScreenHandlerContext.create(w, p));
                        }
                    });
                } else if (payload.screen() == RequestOpenScreenC2SPayload.Screen.PORTABLE_CRAFTING) {
                    if (checkAndRejectIfNotEnabled(player)) return;
                    UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
                    boolean hasCraftingUpgrade = false;
                    for (int i = 0; i < upgrades.getSlotCount(); i++) {
                        ItemStack st = upgrades.getStack(i);
                        if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.CRAFTING_TABLE && !upgrades.isSlotDisabled(i, player)) {
                            hasCraftingUpgrade = true;
                            break;
                        }
                    }
                    if (!hasCraftingUpgrade) return;
                    player.closeHandledScreen();
                    player.openHandledScreen(new net.minecraft.screen.NamedScreenHandlerFactory() {
                        @Override
                        public net.minecraft.text.Text getDisplayName() {
                            return net.minecraft.text.Text.translatable("container.crafting");
                        }
                        @Override
                        public net.minecraft.screen.ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, net.minecraft.entity.player.PlayerEntity p) {
                            return new PortableCraftingScreenHandler(syncId, inv, net.minecraft.screen.ScreenHandlerContext.create(player.getWorld(), player.getBlockPos()));
                        }
                    });
                } else if (payload.screen() == RequestOpenScreenC2SPayload.Screen.FILTER_MAIN) {
                    // 筛选系统主界面 - 客户端处理，服务器端无需特殊处理
                    // 客户端会直接打开FilterMainScreen
                } else if (payload.screen() == RequestOpenScreenC2SPayload.Screen.FILTER_SCREEN) {
                    // 筛选界面 - 客户端处理，服务器端无需特殊处理
                    // 客户端会直接打开FilterScreen
                } else if (payload.screen() == RequestOpenScreenC2SPayload.Screen.DESTROY_SCREEN) {
                    // 销毁界面 - 客户端处理，服务器端无需特殊处理
                    // 客户端会直接打开DestroyScreen
                } else if (payload.screen() == RequestOpenScreenC2SPayload.Screen.BARREL_FILTER) {
                    // 绑定木桶筛选界面 - 客户端处理，服务器端无需特殊处理
                    // 客户端会直接打开FilterMainScreen，但数据来源是绑定木桶
                }
            });
        });

	}


	// ===== XP 计算与增减（基于等级与进度的精确转换） =====
	private static int totalXpForLevel(int level) {
		if (level <= 16) return level * level + 6 * level;
		if (level <= 31) return (int)Math.floor(2.5 * level * level - 40.5 * level + 360);
		return (int)Math.floor(4.5 * level * level - 162.5 * level + 2220);
	}

	private static int xpToNextLevel(int level) {
		if (level <= 15) return 2 * level + 7;
		if (level <= 30) return 5 * level - 38;
		return 9 * level - 158;
	}

	// 新的经验值操作：使用等级和经验值混合，避免残留
	private static int depositPlayerXpByLevels(ServerPlayerEntity player, int levels) {
		if (levels <= 0) return 0;
		
		int currentLevel = player.experienceLevel;
		float currentProgress = player.experienceProgress;
		int xpInCurrentLevel = (int) (currentProgress * xpToNextLevel(currentLevel));
		
		int totalDeposited = 0;
		
		// 先扣除当前等级的零头
		if (xpInCurrentLevel > 0) {
			totalDeposited += xpInCurrentLevel;
			player.experienceProgress = 0.0f;
		}
		
		// 然后逐级降低等级
		for (int i = 0; i < levels && currentLevel > 0; i++) {
			currentLevel--;
			int xpForThisLevel = xpToNextLevel(currentLevel);
			totalDeposited += xpForThisLevel;
			player.experienceLevel = currentLevel;
		}
		
		// 刷新客户端经验条显示（不改变经验值，只触发同步）
		player.addExperience(0);
		
		return totalDeposited;
	}

	private static int withdrawPlayerXpByLevels(ServerPlayerEntity player, int levels) {
		if (levels <= 0) return 0;
		
		int currentLevel = player.experienceLevel;
		float currentProgress = player.experienceProgress;
		int xpInCurrentLevel = (int) (currentProgress * xpToNextLevel(currentLevel));
		
		int totalWithdrawn = 0;
		
		// 先获取当前等级的零头
		if (xpInCurrentLevel > 0) {
			totalWithdrawn += xpInCurrentLevel;
			player.experienceProgress = 0.0f;
		}
		
		// 然后逐级增加等级
		for (int i = 0; i < levels; i++) {
			// 计算从当前等级到下一级还需要多少经验
			int xpNeededToNextLevel = xpToNextLevel(currentLevel) - (int) (player.experienceProgress * xpToNextLevel(currentLevel));
			totalWithdrawn += xpNeededToNextLevel;
			currentLevel++;
			player.experienceLevel = currentLevel;
			player.experienceProgress = 0.0f; // 升级后进度重置为0
		}
		
		// 刷新客户端经验条显示（不改变经验值，只触发同步）
		player.addExperience(0);
		
		return totalWithdrawn;
	}

	private static int xpForLevels(int baseLevel, int levels) {
		int from = totalXpForLevel(baseLevel);
		int to = totalXpForLevel(baseLevel + levels);
		return Math.max(0, to - from);
	}


	private static java.util.UUID getOwnerUuidFromItem(net.minecraft.item.ItemStack stack) {
		try {
			net.minecraft.component.type.NbtComponent comp = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
			if (comp == null) return null;
			net.minecraft.nbt.NbtCompound nbt = comp.copyNbt();
			if (nbt.contains("ps_owner_uuid_most") && nbt.contains("ps_owner_uuid_least")) {
				return new java.util.UUID(nbt.getLong("ps_owner_uuid_most"), nbt.getLong("ps_owner_uuid_least"));
			}
			if (nbt.contains("ps_owner_uuid")) {
				return nbt.getUuid("ps_owner_uuid");
			}
		} catch (Throwable ignored) {}
		return null;
	}


    private static void insertIntoPlayerInventory(ServerPlayerEntity player, ItemStack stack) {
		Inventory inv = player.getInventory();
		if (stack.isEmpty()) return;
		
		// 只尝试插入到主物品栏+快捷栏（槽位0-35），不包括装备栏和副手
		// 先尝试合并
		for (int i = 0; i <= 35 && !stack.isEmpty(); i++) {
			ItemStack cur = inv.getStack(i);
			if (!cur.isEmpty() && ItemStack.areItemsAndComponentsEqual(cur, stack)) {
				int max = Math.min(cur.getMaxCount(), inv.getMaxCountPerStack());
				int can = Math.min(stack.getCount(), max - cur.getCount());
				if (can > 0) {
					cur.increment(can);
					stack.decrement(can);
					inv.markDirty();
				}
			}
		}
		// 放到空位
		for (int i = 0; i <= 35 && !stack.isEmpty(); i++) {
			if (inv.getStack(i).isEmpty()) {
				inv.setStack(i, stack.copy());
				stack.setCount(0);
				inv.markDirty();
				break;
			}
		}
		
		// 如果还有剩余物品，丢到地上
		if (!stack.isEmpty()) {
			player.dropItem(stack, false);
		}
	}


    // 新版：存入到分离式玩家存储（只增 PlayerStore，不写入旧 StorageInventory）
    private static ItemStack insertIntoNewStorage(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var server = player.getServer();
        if (server == null) return stack;
        
        // 检查容量限制
        PlayerStorageAccess access = (PlayerStorageAccess) player;
        StorageType storageType = access.portableStorage$getStorageType();
        if (storageType.hasCapacityLimit()) {
            // 检查是否已达到容量限制
            if (!NewStoreService.canAddNewItemType(player, stack)) {
                // 容量限制，返回原物品
                return stack;
            }
        }
        
        // 直接调用 NewStoreService 确保使用统一逻辑
        NewStoreService.insertForOnlinePlayer(player, stack);
        return ItemStack.EMPTY;
    }

    public static void sendSync(ServerPlayerEntity player) {
        StorageInventory merged = buildMergedSnapshot(player);
        NbtCompound nbt = new NbtCompound();
        // 重置玩家会话并写入新的 sessionId，客户端据此重置 expectedSeq
        StorageSyncManager.startNewSession(player.getUuid());
        long sid = StorageSyncManager.getOrStartSession(player.getUuid());
        nbt.putLong("sessionId", sid);
        // 使用玩家注册表上下文，确保附魔等基于注册表的数据正确序列化
        merged.writeNbt(nbt, player.getRegistryManager());
        ServerPlayNetworking.send(player, new StorageSyncS2CPayload(nbt));
        // 刷新服务器端"上次快照"，用于后续生成真实 diff
        StorageSyncManager.setLastSnapshot(player.getUuid(), toSnapshotMap(merged));
        sendUpgradeSync(player);
        sendEnablementSync(player);
        sendInfiniteFluidConfigSync(player);
        sendRiftConfigSync(player);
        sendVirtualCraftingConfigSync(player);
        sendContainerDisplayConfigSync(player);
    }

    private static void sendIncrementalAll(ServerPlayerEntity player) {
        // 会话不重置，仅生成下一序号
        long sid = StorageSyncManager.getOrStartSession(player.getUuid());
        int seq = StorageSyncManager.nextSeq(player.getUuid());
        StorageInventory cur = buildMergedSnapshot(player);
        NbtCompound diff = buildRealDiffFromSnapshots(player, cur);
        // 分包：按配置上限切分 upserts+removes
        int maxEntries = 512;
        try {
            maxEntries = Math.max(1, ServerConfig.getInstance().getIncrementalSyncMaxEntries());
        } catch (Throwable ignored) {}
        java.util.List<NbtCompound> chunks = splitDiff(diff, maxEntries);
        for (NbtCompound part : chunks) {
            ServerPlayNetworking.send(player, new IncrementalStorageSyncS2CPayload(sid, seq, part));
            seq = StorageSyncManager.nextSeq(player.getUuid());
        }
        // 更新快照
        StorageSyncManager.setLastSnapshot(player.getUuid(), toSnapshotMap(cur));
    }

    private static NbtCompound buildRealDiffFromSnapshots(ServerPlayerEntity player, StorageInventory current) {
        NbtCompound diff = new NbtCompound();
        net.minecraft.nbt.NbtList upserts = new net.minecraft.nbt.NbtList();
        net.minecraft.nbt.NbtList removes = new net.minecraft.nbt.NbtList();
        java.util.Map<String, StorageSyncManager.SnapshotEntry> last = StorageSyncManager.getLastSnapshot(player.getUuid());
        java.util.Set<String> visited = new java.util.HashSet<>();
        // upsert：当前存在且与上次不同
        for (int i = 0; i < current.getCapacity(); i++) {
            ItemStack disp = current.getDisplayStack(i);
            long cnt = current.getCountByIndex(i);
            if (disp.isEmpty() || cnt <= 0) continue;
            String key = makeKeyForStack(disp);
            long ts = current.getTimestampByIndex(i);
            StorageSyncManager.SnapshotEntry pre = (last != null) ? last.get(key) : null;
            if (pre == null || pre.count != cnt || pre.timestamp != ts) {
                NbtCompound e = new NbtCompound();
                e.putString("key", key);
                e.putLong("count", cnt);
                e.putLong("ts", ts);
                final var lookup = player.getRegistryManager();
                final var ops = (lookup != null)
                    ? net.minecraft.registry.RegistryOps.of(net.minecraft.nbt.NbtOps.INSTANCE, lookup)
                    : net.minecraft.nbt.NbtOps.INSTANCE;
                var encoded = ItemStack.CODEC.encodeStart(ops, disp.copy());
                encoded.result().ifPresent(n -> e.put("display", n));
                upserts.add(e);
            }
            visited.add(key);
        }
        // removes：上次有但这次没有
        if (last != null) {
            for (var entry : last.entrySet()) {
                if (!visited.contains(entry.getKey())) {
                    removes.add(net.minecraft.nbt.NbtString.of(entry.getKey()));
                }
            }
        }
        if (!upserts.isEmpty()) diff.put("upserts", upserts);
        if (!removes.isEmpty()) diff.put("removes", removes);
        return diff;
    }

    private static java.util.List<NbtCompound> splitDiff(NbtCompound diff, int maxEntries) {
        java.util.ArrayList<NbtCompound> parts = new java.util.ArrayList<>();
        net.minecraft.nbt.NbtList up = diff.contains("upserts", net.minecraft.nbt.NbtElement.LIST_TYPE) ? diff.getList("upserts", net.minecraft.nbt.NbtElement.COMPOUND_TYPE) : new net.minecraft.nbt.NbtList();
        net.minecraft.nbt.NbtList rm = diff.contains("removes", net.minecraft.nbt.NbtElement.LIST_TYPE) ? diff.getList("removes", net.minecraft.nbt.NbtElement.STRING_TYPE) : new net.minecraft.nbt.NbtList();
        int ui = 0, ri = 0;
        while (ui < up.size() || ri < rm.size()) {
            NbtCompound part = new NbtCompound();
            net.minecraft.nbt.NbtList upPart = new net.minecraft.nbt.NbtList();
            net.minecraft.nbt.NbtList rmPart = new net.minecraft.nbt.NbtList();
            int count = 0;
            while (ui < up.size() && count < maxEntries) { upPart.add(up.get(ui++)); count++; }
            while (ri < rm.size() && count < maxEntries) { rmPart.add(rm.get(ri++)); count++; }
            if (!upPart.isEmpty()) part.put("upserts", upPart);
            if (!rmPart.isEmpty()) part.put("removes", rmPart);
            parts.add(part);
        }
        if (parts.isEmpty()) {
            parts.add(new NbtCompound());
        }
        return parts;
    }

    private static java.util.Map<String, StorageSyncManager.SnapshotEntry> toSnapshotMap(StorageInventory inv) {
        java.util.Map<String, StorageSyncManager.SnapshotEntry> map = new java.util.HashMap<>();
        for (int i = 0; i < inv.getCapacity(); i++) {
            ItemStack disp = inv.getDisplayStack(i);
            long cnt = inv.getCountByIndex(i);
            if (disp.isEmpty() || cnt <= 0) continue;
            String key = makeKeyForStack(disp);
            long ts = inv.getTimestampByIndex(i);
            map.put(key, new StorageSyncManager.SnapshotEntry(cnt, ts));
        }
        return map;
    }

	private static String makeKeyForStack(ItemStack s) {
		// 直接使用 ItemKeyHasher 生成键，确保与新版储存系统完全一致
		return com.portable.storage.newstore.ItemKeyHasher.hash(s, null);
	}
	
	/**
	 * 发送增量同步（初版：全量式 upsert，以打通协议与会话/序号）
	 */
	public static void sendIncrementalSync(ServerPlayerEntity player) {
		sendIncrementalAll(player);
	}
	
	/**
	 * 发送按需增量同步（初版：全量式 upsert）
	 */
	public static void sendIncrementalSyncOnDemand(ServerPlayerEntity player) {
		sendIncrementalAll(player);
	}

	/**
	 * 按玩家视角方向丢出物品，速度与原版 Q 丢弃相近。
	 */
	private static void dropItemTowardsLook(ServerPlayerEntity player, ItemStack stack) {
		// 先生成掉落实体
		ItemStack copy = stack.copy();
		stack.setCount(0);
		var itemEntity = new net.minecraft.entity.ItemEntity(
			player.getWorld(),
			player.getX(), player.getEyeY() - 0.3, player.getZ(),
			copy
		);
		// 初始位置稍微在玩家前方
		net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0f).normalize();
		net.minecraft.util.math.Vec3d spawnOffset = look.multiply(0.3);
		itemEntity.setPos(itemEntity.getX() + spawnOffset.x, itemEntity.getY() + spawnOffset.y, itemEntity.getZ() + spawnOffset.z);
		// 赋予速度（接近原版 Q 丢出：略带随机与向下分量）
		double speed = 0.35D;
		net.minecraft.util.math.Vec3d vel = look.multiply(speed);
		// 轻微下坠感
		vel = vel.add(0, 0.1D, 0);
		itemEntity.setVelocity(vel);
		itemEntity.setPickupDelay(10);
		player.getWorld().spawnEntity(itemEntity);
	}

	public static void sendUpgradeSync(ServerPlayerEntity player) {
		UpgradeInventory up = PlayerStorageService.getUpgradeInventory(player);
		NbtCompound nbt = new NbtCompound();
		up.writeNbt(nbt);
		{
			NbtCompound data = nbt;
			ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
				ConfigSyncS2CPayload.Topic.UPGRADE, data
			));
		}
		// 同步XP步长
		{
			int xpStep = xpStepIndexByPlayer.getOrDefault(player.getUuid(), 0);
			NbtCompound data = new NbtCompound();
			data.putInt("stepIndex", xpStep);
			ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
				ConfigSyncS2CPayload.Topic.XP_STEP, data
			));
		}
		// 同步自动进食模式
		{
			AutoEatMode mode = autoEatModeByPlayer.getOrDefault(player.getUuid(), AutoEatMode.DEFAULT);
			NbtCompound data = new NbtCompound();
			data.putInt("modeIndex", mode.getIndex());
			ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
				ConfigSyncS2CPayload.Topic.AUTO_EAT_MODE, data
			));
		}
	}
	
	public static void sendEnablementSync(ServerPlayerEntity player) {
		PlayerStorageAccess access = (PlayerStorageAccess) player;
		boolean enabled = access.portableStorage$isStorageEnabled();
		StorageType storageType = access.portableStorage$getStorageType();
		NbtCompound data = new NbtCompound();
		data.putBoolean("enabled", enabled);
		data.putString("storageType", storageType.getKey());
		ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
			ConfigSyncS2CPayload.Topic.STORAGE_ENABLEMENT, data
		));
	}
	
	public static void sendInfiniteFluidConfigSync(ServerPlayerEntity player) {
		ServerConfig config = ServerConfig.getInstance();
		NbtCompound data = new NbtCompound();
		data.putBoolean("enableInfiniteLava", config.isEnableInfiniteLava());
		data.putBoolean("enableInfiniteWater", config.isEnableInfiniteWater());
		data.putInt("infiniteLavaThreshold", config.getInfiniteLavaThreshold());
		data.putInt("infiniteWaterThreshold", config.getInfiniteWaterThreshold());
		ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
			ConfigSyncS2CPayload.Topic.INFINITE_FLUID_CONFIG, data
		));
	}
	
	public static void sendRiftConfigSync(ServerPlayerEntity player) {
		ServerConfig config = ServerConfig.getInstance();
		NbtCompound data = new NbtCompound();
		data.putString("riftUpgradeItem", config.getRiftUpgradeItem());
		data.putInt("riftSize", config.getRiftSize());
		ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
			ConfigSyncS2CPayload.Topic.RIFT_CONFIG, data
		));
	}
	
	public static void sendVirtualCraftingConfigSync(ServerPlayerEntity player) {
		ServerConfig config = ServerConfig.getInstance();
		NbtCompound data = new NbtCompound();
		data.putBoolean("enableVirtualCrafting", config.isEnableVirtualCrafting());
		ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
			ConfigSyncS2CPayload.Topic.VIRTUAL_CRAFTING_CONFIG, data
		));
	}
	
	public static void sendContainerDisplayConfigSync(ServerPlayerEntity player) {
		ServerConfig config = ServerConfig.getInstance();
		NbtCompound data = new NbtCompound();
		data.putBoolean("stonecutter", config.isStonecutter());
		data.putBoolean("cartographyTable", config.isCartographyTable());
		data.putBoolean("smithingTable", config.isSmithingTable());
		data.putBoolean("grindstone", config.isGrindstone());
		data.putBoolean("loom", config.isLoom());
		data.putBoolean("furnace", config.isFurnace());
		data.putBoolean("smoker", config.isSmoker());
		data.putBoolean("blastFurnace", config.isBlastFurnace());
		data.putBoolean("anvil", config.isAnvil());
		data.putBoolean("enchantingTable", config.isEnchantingTable());
		data.putBoolean("brewingStand", config.isBrewingStand());
		data.putBoolean("beacon", config.isBeacon());
		data.putBoolean("chest", config.isChest());
		data.putBoolean("barrel", config.isBarrel());
		data.putBoolean("enderChest", config.isEnderChest());
		data.putBoolean("shulkerBox", config.isShulkerBox());
		data.putBoolean("dispenser", config.isDispenser());
		data.putBoolean("dropper", config.isDropper());
		data.putBoolean("crafter", config.isCrafter());
		data.putBoolean("hopper", config.isHopper());
		data.putBoolean("trappedChest", config.isTrappedChest());
		data.putBoolean("hopperMinecart", config.isHopperMinecart());
		data.putBoolean("chestMinecart", config.isChestMinecart());
		data.putBoolean("chestBoat", config.isChestBoat());
		data.putBoolean("bambooChestRaft", config.isBambooChestRaft());
		ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
			ConfigSyncS2CPayload.Topic.DISPLAY_CONFIG, data
		));
	}

    // ===== 合并仓库（共享木桶） =====
    private static java.util.List<StorageInventory> getViewStorages(ServerPlayerEntity viewer) {
        java.util.List<StorageInventory> list = new java.util.ArrayList<>();
        java.util.LinkedHashSet<java.util.UUID> added = new java.util.LinkedHashSet<>();
        // 自己优先
        list.add(PlayerStorageService.getInventory(viewer));
        added.add(viewer.getUuid());

        // 统计"我所依附的根拥有者集合"
        java.util.LinkedHashSet<java.util.UUID> rootOwners = new java.util.LinkedHashSet<>();
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(viewer);
        for (int i = 0; i < upgrades.getSlotCount(); i++) {
            ItemStack st = upgrades.getStack(i);
            if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.BARREL) {
                java.util.UUID owner = getOwnerUuidFromItem(st);
                if (owner != null && !owner.equals(viewer.getUuid())) {
                    rootOwners.add(owner);
                }
            }
        }

        // 如果未依附任何人，则自己就是根拥有者之一（用于处理"我是源头"，让使用我木桶的人加入）
        if (rootOwners.isEmpty()) {
            rootOwners.add(viewer.getUuid());
        }

        var players = viewer.server.getPlayerManager().getPlayerList();

        for (java.util.UUID root : rootOwners) {
            // 加入根拥有者仓库
            var ownerPlayer = viewer.server.getPlayerManager().getPlayer(root);
            if (ownerPlayer != null) {
                if (added.add(root)) list.add(PlayerStorageService.getInventory(ownerPlayer));
            } else {
                // 离线：从存档加载
                if (added.add(root)) list.add(StoragePersistence.loadStorage(viewer.server, root));
            }

            // 加入所有"同依附该根拥有者"的玩家（包括 viewer 自己，已去重）
            for (ServerPlayerEntity p : players) {
                UpgradeInventory up = PlayerStorageService.getUpgradeInventory(p);
                boolean usesRoot = false;
                for (int i = 0; i < up.getSlotCount(); i++) {
                    ItemStack st = up.getStack(i);
                    if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.BARREL) {
                        java.util.UUID owner = getOwnerUuidFromItem(st);
                        if (owner != null && owner.equals(root)) { usesRoot = true; break; }
                    }
                }
                if (usesRoot && added.add(p.getUuid())) {
                    list.add(PlayerStorageService.getInventory(p));
                }
            }
        }

        return list;
    }

    // ===== 统一动作处理 =====
    private static void handleStorageAction(ServerPlayerEntity player, StorageActionC2SPayload p) {
        switch (p.action()) {
            case CLICK -> handleStorageClick(player, p.index(), p.button());
            case SHIFT_TAKE -> handleStorageShiftTake(player, p.index(), p.button());
            case DROP -> handleStorageDrop(player, p.index(), p.amountType());
            case DEPOSIT_CURSOR -> handleDepositCursor(player, p.button());
            case DEPOSIT_SLOT -> handleSlotDepositAction(player, p);
        }
    }

    private static void handleUpgradeAction(ServerPlayerEntity player, StorageActionC2SPayload p) {
        int slot = p.index();
        int button = p.button();
        // 直接复用原 UpgradeSlotClick 逻辑主体
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        if (slot < 0 || slot >= upgrades.getSlotCount()) return;
        if (button == 1) {
            if (slot == 4 && !upgrades.isSlotDisabled(4, player) && !upgrades.getStack(4).isEmpty()) {
                // 裂隙升级槽：右键在维度间来回切换
                handleRiftTeleport(player);
                return;
            }
            if (slot == 6 && upgrades.isBedUpgradeActive()) {
                // 检查仓库是否启用
                com.portable.storage.player.PlayerStorageAccess access = (com.portable.storage.player.PlayerStorageAccess) player;
                if (access.portableStorage$isStorageEnabled()) {
                    handleBedUpgradeSleep(player);
                }
                return;
            }
            if (slot == 7 && !upgrades.isSlotDisabled(7, player) && !upgrades.getStack(7).isEmpty()) {
                int idx = xpStepIndexByPlayer.getOrDefault(player.getUuid(), 0);
                idx = (idx + 1) % XP_STEPS.length;
                xpStepIndexByPlayer.put(player.getUuid(), idx);
                int step = XP_STEPS[idx];
                player.sendMessage(net.minecraft.text.Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.step", step), true);
                net.minecraft.nbt.NbtCompound data = new net.minecraft.nbt.NbtCompound();
                data.putInt("stepIndex", idx);
                ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
                    ConfigSyncS2CPayload.Topic.XP_STEP, data
                ));
                return;
            }
            upgrades.toggleSlotDisabled(slot);
            sendUpgradeSync(player);
            return;
        }
        
        if (button == 2) { // 中键点击
            if (slot == 9 && !upgrades.isSlotDisabled(9, player) && !upgrades.getStack(9).isEmpty()) {
                // 附魔金苹果升级槽：中键切换自动进食模式
                AutoEatMode currentMode = autoEatModeByPlayer.getOrDefault(player.getUuid(), AutoEatMode.DEFAULT);
                AutoEatMode nextMode = currentMode.next();
                autoEatModeByPlayer.put(player.getUuid(), nextMode);
                
                // 发送消息给玩家
                String modeName = Text.translatable(PortableStorage.MOD_ID + ".enchanted_golden_apple.mode." + nextMode.getKey()).getString();
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".enchanted_golden_apple.mode_switched", modeName), true);
                
                // 同步模式到客户端
                net.minecraft.nbt.NbtCompound data = new net.minecraft.nbt.NbtCompound();
                data.putInt("modeIndex", nextMode.getIndex());
                ServerPlayNetworking.send(player, new ConfigSyncS2CPayload(
                    ConfigSyncS2CPayload.Topic.AUTO_EAT_MODE, data
                ));
                return;
            }
            // 其他槽位中键：切换禁用状态
            upgrades.toggleSlotDisabled(slot);
            sendUpgradeSync(player);
            return;
        }

        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        ItemStack slotStack = upgrades.getStack(slot);
        if (cursor.isEmpty()) {
            if (!slotStack.isEmpty()) {
                ItemStack taken = upgrades.takeStack(slot);
                if (slot == 2 && taken.getItem() == net.minecraft.item.Items.CHEST) {
                    handleChestUpgradeRemoval(player, upgrades);
                }
                player.currentScreenHandler.setCursorStack(taken);
                player.currentScreenHandler.sendContentUpdates();
                sendUpgradeSync(player);
            }
        } else {
            if (slotStack.isEmpty()) {
                if (!cursor.isEmpty()) {
                    ItemStack one = cursor.copy();
                    one.setCount(1);
                    if (upgrades.tryInsert(slot, one, player, null, null)) {
                        // 成功放入一个，减少手中物品1个
                        cursor.decrement(1);
                        if (cursor.isEmpty()) {
                            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                        } else {
                            player.currentScreenHandler.setCursorStack(cursor);
                        }
                        player.currentScreenHandler.sendContentUpdates();
                        sendSync(player);
                    }
                }
            } else {
                if (cursor.getCount() == 1) {
                    ItemStack taken = upgrades.takeStack(slot);
                    if (upgrades.tryInsert(slot, cursor, player, null, null)) {
                        player.currentScreenHandler.setCursorStack(taken);
                        player.currentScreenHandler.sendContentUpdates();
                            sendSync(player);
                    } else {
                        upgrades.setStack(slot, taken);
                    }
                }
            }
        }
    }

    private static void handleRiftTeleport(ServerPlayerEntity player) {
        try {
            net.minecraft.server.MinecraftServer server = player.getServer();
            if (server == null) return;
            net.minecraft.server.world.ServerWorld rift = SpaceRiftManager.getWorld(server);
            if (rift == null) {
                // 维度未加载（数据驱动注册失败？）
                player.sendMessage(net.minecraft.text.Text.literal("Space Rift dimension not found"), true);
                return;
            }

            if (player.getWorld() == rift) {
                // 在裂隙内：返回
                java.util.UUID id = player.getUuid();
                net.minecraft.util.math.GlobalPos back = SpaceRiftManager.getReturnPoint(id);
                if (back != null) {
                    net.minecraft.server.world.ServerWorld targetWorld = server.getWorld(back.dimension());
                    if (targetWorld != null) {
                        SpaceRiftManager.clearReturnPoint(id);
                        SpaceRiftManager.resetToWorldBorder(player);
                        // 离开裂隙时创建复制体
                        SpaceRiftManager.ensureAvatarOnExit(player);
                        net.minecraft.util.math.BlockPos p = back.pos();
                        player.teleport(targetWorld, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYaw(), player.getPitch());
                        return;
                    }
                }
                // 兜底：主世界出生点
                SpaceRiftManager.ensureAvatarOnExit(player);
                net.minecraft.server.world.ServerWorld overworld = server.getOverworld();
                net.minecraft.util.math.BlockPos spawn = overworld.getSpawnPos();
                player.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), player.getPitch());
                return;
            }

            // 从其他维度进入裂隙
            // 检查裂隙功能是否启用
            if (!ServerConfig.getInstance().isEnableRiftFeature()) {
                player.sendMessage(net.minecraft.text.Text.translatable(PortableStorage.MOD_ID + ".rift_feature_disabled"), true);
                return;
            }
            
            SpaceRiftManager.rememberReturnPoint(player);
            java.util.UUID id = player.getUuid();
            net.minecraft.util.math.ChunkPos origin = SpaceRiftManager.ensureAllocatedPlot(server, id);
            // 初始化平台与屏障
            SpaceRiftManager.ensurePlotInitialized(rift, origin);
            
            // 优先传送到复制体位置，否则传送到地块中心
            net.minecraft.util.math.BlockPos teleportPos = SpaceRiftManager.getAvatarPositionOrCenter(player, origin);
            
            // 进入裂隙时清除复制体
            SpaceRiftManager.removeAvatar(player);
            player.teleport(rift, teleportPos.getX() + 0.5, teleportPos.getY(), teleportPos.getZ() + 0.5, player.getYaw(), player.getPitch());
            SpaceRiftManager.applyPersonalBorder(player);
        } catch (Exception e) {
            player.sendMessage(net.minecraft.text.Text.literal("Teleport failed: " + e.getMessage()), true);
        }
    }

    private static void handleTrashAction(ServerPlayerEntity player, StorageActionC2SPayload p) {
        int button = p.button();
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        
        if (button == 0) {
            // 左键：如果鼠标有物品则放入，如果鼠标没有物品则取出
            ItemStack cursor = player.currentScreenHandler.getCursorStack();
            if (!cursor.isEmpty()) {
                // 鼠标有物品：放入到垃圾桶槽位
                if (upgrades.tryInsertTrashSlot(cursor)) {
                    player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                    player.currentScreenHandler.sendContentUpdates();
                    sendUpgradeSync(player);
                }
            } else {
                // 鼠标没有物品：从垃圾桶槽位取出
                if (upgrades.isTrashSlotActive()) {
                    ItemStack trashItem = upgrades.takeTrashSlot();
                    if (!trashItem.isEmpty()) {
                        player.currentScreenHandler.setCursorStack(trashItem);
                        player.currentScreenHandler.sendContentUpdates();
                        sendUpgradeSync(player);
                    }
                }
            }
        } else if (button == 1) {
            // 右键：清空垃圾桶槽位
            if (upgrades.isTrashSlotActive()) {
                upgrades.setTrashSlot(ItemStack.EMPTY);
                sendUpgradeSync(player);
            }
        }
    }

    private static void handleFluidAction(ServerPlayerEntity player, StorageActionC2SPayload p) {
        // 两类：fluid slot click（target=FLUID, action=CLICK，index忽略） 和 虚拟fluid点击（target=FLUID, action=CLICK with resourceType）
        String fluidType = p.resourceType();
        int button = p.button();
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);

        if (fluidType != null && !fluidType.isEmpty()) {
            // 对应虚拟流体条目点击（FluidClick/FluidConversion 合并）
            if (button == 0) {
                // 左键：从背包中消耗一个空桶，取出对应的流体桶
                int units = upgrades.getFluidUnits(fluidType);
                if (units > 0) {
                    // 在背包中查找空桶
                    ItemStack bucketToConsume = null;
                    int bucketSlot = -1;
                    var handler = player.currentScreenHandler;
                    for (int i = 0; i < handler.slots.size(); i++) {
                        var slot = handler.slots.get(i);
                        if (slot.hasStack() && slot.getStack().isOf(net.minecraft.item.Items.BUCKET)) {
                            bucketToConsume = slot.getStack();
                            bucketSlot = i;
                            break;
                        }
                    }
                    if (bucketToConsume != null) {
                        bucketToConsume.decrement(1);
                        if (bucketToConsume.isEmpty()) {
                            handler.slots.get(bucketSlot).setStack(ItemStack.EMPTY);
                        }
                        upgrades.removeFluidUnits(fluidType, 1);
                        ItemStack fluidBucket = UpgradeInventory.createFluidBucket(fluidType);
                        insertIntoPlayerInventory(player, fluidBucket);
                        player.currentScreenHandler.sendContentUpdates();
                                sendSync(player);
                    }
                }
            } else if (button == 1) {
                ItemStack cursor = player.currentScreenHandler.getCursorStack();
                if (!cursor.isEmpty() && cursor.isOf(UpgradeInventory.createFluidBucket(fluidType).getItem())) {
                    upgrades.addFluidUnits(fluidType, 1);
                    player.currentScreenHandler.setCursorStack(new ItemStack(net.minecraft.item.Items.BUCKET));
                    player.currentScreenHandler.sendContentUpdates();
                                sendSync(player);
                }
            }
            return;
        }

        // 对应 FluidSlotClickC2SPayload（使用 button）
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        ItemStack fluidStack = upgrades.getFluidStack();
        if (cursor.isEmpty()) {
            if (!fluidStack.isEmpty()) {
                upgrades.setFluidStack(ItemStack.EMPTY);
                player.currentScreenHandler.setCursorStack(fluidStack);
                player.currentScreenHandler.sendContentUpdates();
                        sendSync(player);
            }
        } else if (UpgradeInventory.isValidFluidItem(cursor)) {
            if (cursor.isOf(net.minecraft.item.Items.BUCKET)) {
                boolean hasFluid = false;
                for (String ft : new String[]{"lava", "water", "milk"}) {
                    if (upgrades.getFluidUnits(ft) > 0) {
                        upgrades.removeFluidUnits(ft, 1);
                        player.currentScreenHandler.setCursorStack(UpgradeInventory.createFluidBucket(ft));
                        hasFluid = true;
                        break;
                    }
                }
                if (!hasFluid) {
                    upgrades.setFluidStack(cursor.copy());
                    player.currentScreenHandler.setCursorStack(fluidStack);
                }
            } else {
                String ft = UpgradeInventory.getFluidType(cursor);
                if (ft != null) {
                    upgrades.addFluidUnits(ft, 1);
                    player.currentScreenHandler.setCursorStack(new ItemStack(net.minecraft.item.Items.BUCKET));
                } else {
                    upgrades.setFluidStack(cursor.copy());
                    player.currentScreenHandler.setCursorStack(fluidStack);
                }
            }
            player.currentScreenHandler.sendContentUpdates();
                    sendSync(player);
        }
    }

    private static void handleXpBottleAction(ServerPlayerEntity player, StorageActionC2SPayload p) {
        int button = p.button();
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        if (upgrades.isSlotDisabled(7, player) || upgrades.getStack(7).isEmpty()) return;
        if (upgrades.isLevelMaintenanceEnabled()) {
            player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.maintenance_blocked"), true);
            return;
        }
        // 右键优先处理"玻璃瓶转换为附魔之瓶"的需求
        if (button == 1) {
            ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
            if (!cursorStack.isEmpty() && cursorStack.isOf(net.minecraft.item.Items.GLASS_BOTTLE)) {
                int bottleCount = cursorStack.getCount();
                long availableXp = upgrades.getXpPool();
                int maxConvertible = (int) Math.min(bottleCount, availableXp / 11);
                if (maxConvertible > 0) {
                    long xpUsed = maxConvertible * 11L;
                    upgrades.removeFromXpPool(xpUsed);
                    ItemStack experienceBottles = new ItemStack(net.minecraft.item.Items.EXPERIENCE_BOTTLE, maxConvertible);
                    int remainingBottles = bottleCount - maxConvertible;
                    if (remainingBottles > 0) {
                        // 将多余玻璃瓶存仓，给玩家转好的瓶子
                        // 新版：存入分离存储
                        ItemStack remainingBottleStack = new ItemStack(net.minecraft.item.Items.GLASS_BOTTLE, remainingBottles);
                        insertIntoNewStorage(player, remainingBottleStack);
                        player.currentScreenHandler.setCursorStack(experienceBottles);
                        player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.conversion.partial", maxConvertible, remainingBottles), true);
                    } else {
                        player.currentScreenHandler.setCursorStack(experienceBottles);
                        player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.conversion.complete", maxConvertible), true);
                    }
                    sendUpgradeSync(player);
                    sendSync(player);
                    return;
                } else {
                    player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.conversion.insufficient_xp"), true);
                    return;
                }
            }
        }
        int idx = xpStepIndexByPlayer.getOrDefault(player.getUuid(), 0);
        int levels = XP_STEPS[idx];
        if (button == 0) {
            int xpNeeded = xpForLevels(player.experienceLevel, levels);
            long availableXp = upgrades.getXpPool();
            if (availableXp >= xpNeeded) {
                long taken = upgrades.removeFromXpPool(xpNeeded);
                if (taken > 0) {
                    int actualWithdrawn = withdrawPlayerXpByLevels(player, levels);
                    player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.delta", "+" + actualWithdrawn), true);
                }
            } else if (availableXp > 0) {
                long taken = upgrades.removeFromXpPool(availableXp);
                if (taken > 0) {
                    player.addExperience((int)Math.min(Integer.MAX_VALUE, taken));
                    player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.delta", "+" + taken), true);
                }
            }
            sendUpgradeSync(player);
        } else {
            int deposited = depositPlayerXpByLevels(player, levels);
            if (deposited > 0) {
                upgrades.addToXpPool(deposited);
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.delta", "-" + deposited), true);
            }
            sendUpgradeSync(player);
        }
    }

    private static void handleSlotDepositAction(ServerPlayerEntity player, StorageActionC2SPayload p) {
        int handlerSlotId = p.handlerSlotId();
        ScreenHandler sh = player.currentScreenHandler;
        if (handlerSlotId < 0 || handlerSlotId >= sh.slots.size()) return;
        Slot slot = sh.getSlot(handlerSlotId);
        ItemStack from = slot.getStack();
        if (from.isEmpty()) return;
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        if (UpgradeInventory.isValidFluidItem(from) && !from.isOf(net.minecraft.item.Items.BUCKET)) {
            String fluidType = UpgradeInventory.getFluidType(from);
            if (fluidType != null) {
                int count = from.getCount();
                upgrades.addFluidUnits(fluidType, count);
                ItemStack emptyBuckets = new ItemStack(net.minecraft.item.Items.BUCKET, count);
                // 新版：存入分离存储
                ItemStack r = insertIntoNewStorage(player, emptyBuckets);
                slot.setStack(r);
                slot.markDirty();
                sh.sendContentUpdates();
                sendSync(player);
                return;
            }
        }
        // 新版：存入分离存储
        ItemStack remainder = insertIntoNewStorage(player, from);
        slot.setStack(remainder);
        slot.markDirty();
        sh.sendContentUpdates();
        sendSync(player);
    }

    private static void handleStorageClick(ServerPlayerEntity player, int slotIndex, int button) {
        StorageInventory merged = buildMergedSnapshot(player);
        if (slotIndex < 0 || slotIndex >= merged.getCapacity()) return;
        ItemStack slotStack = merged.getDisplayStack(slotIndex);
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (cursor.isEmpty()) {
            if (slotStack.isEmpty()) return;
            if (button == 0) {
                long want = slotStack.getMaxCount();
                long got = takeFromMerged(player, slotStack, (int) want);
                ItemStack taken = slotStack.copy();
                taken.setCount((int)Math.min(slotStack.getMaxCount(), got));
                player.currentScreenHandler.setCursorStack(taken);
            } else {
                int half = Math.max(1, Math.min(32, (int)Math.ceil(slotStack.getMaxCount() / 2.0)));
                long got = takeFromMerged(player, slotStack, half);
                ItemStack taken = slotStack.copy();
                taken.setCount((int)Math.min(slotStack.getMaxCount(), got));
                player.currentScreenHandler.setCursorStack(taken);
            }
        } else {
            if (button == 0) {
                ItemStack remainder = insertIntoNewStorage(player, cursor);
                player.currentScreenHandler.setCursorStack(remainder);
            } else {
                if (cursor.getCount() > 0) {
                    ItemStack singleStack = cursor.copy();
                    singleStack.setCount(1);
                    ItemStack remainder = insertIntoNewStorage(player, singleStack);
                    if (remainder.isEmpty()) {
                        cursor.decrement(1);
                        player.currentScreenHandler.setCursorStack(cursor);
                    }
                }
            }
        }
        player.currentScreenHandler.sendContentUpdates();
        sendSync(player);
    }

    private static void handleStorageShiftTake(ServerPlayerEntity player, int slotIndex, int button) {
        StorageInventory merged = buildMergedSnapshot(player);
        if (slotIndex < 0 || slotIndex >= merged.getCapacity()) return;
        ItemStack disp = merged.getDisplayStack(slotIndex);
        if (disp.isEmpty()) return;
        // 计算仅主物品栏+快捷栏(0..35)可接纳的最大数量，背包满则不从仓库扣减
        int desired = (button == 1) ? 1 : disp.getMaxCount();
        int canInsert = computeInsertableIntoMainHotbar(player, disp, desired);
        if (canInsert <= 0) return;
        long got = takeFromMerged(player, disp, canInsert);
        if (got <= 0) return;
        insertVariantIntoMainHotbar(player, disp, (int)Math.min(Integer.MAX_VALUE, got));
        sendSync(player);
    }

    /**
     * 计算仅在主物品栏+快捷栏（槽位0..35）内，最多还能插入多少个与 variant 相同的物品。
     */
    private static int computeInsertableIntoMainHotbar(ServerPlayerEntity player, ItemStack variant, int desired) {
        if (desired <= 0) return 0;
        Inventory inv = player.getInventory();
        int remaining = desired;
        // 合并到已有堆叠
        for (int i = 0; i <= 35 && remaining > 0; i++) {
            ItemStack cur = inv.getStack(i);
            if (!cur.isEmpty() && ItemStack.areItemsAndComponentsEqual(cur, variant)) {
                int max = Math.min(cur.getMaxCount(), inv.getMaxCountPerStack());
                int room = Math.max(0, max - cur.getCount());
                if (room > 0) remaining -= Math.min(room, remaining);
            }
        }
        // 空槽容量
        for (int i = 0; i <= 35 && remaining > 0; i++) {
            if (inv.getStack(i).isEmpty()) {
                int max = Math.min(variant.getMaxCount(), inv.getMaxCountPerStack());
                remaining -= Math.min(max, remaining);
            }
        }
        return desired - Math.max(0, remaining);
    }

    /**
     * 仅向主物品栏+快捷栏（槽位0..35）插入给定物品变体的总数量，按先合并后占空位的策略。
     */
    private static void insertVariantIntoMainHotbar(ServerPlayerEntity player, ItemStack variant, int totalCount) {
        if (totalCount <= 0) return;
        Inventory inv = player.getInventory();
        int remaining = totalCount;
        // 合并阶段
        for (int i = 0; i <= 35 && remaining > 0; i++) {
            ItemStack cur = inv.getStack(i);
            if (!cur.isEmpty() && ItemStack.areItemsAndComponentsEqual(cur, variant)) {
                int max = Math.min(cur.getMaxCount(), inv.getMaxCountPerStack());
                int room = Math.max(0, max - cur.getCount());
                if (room > 0) {
                    int add = Math.min(room, remaining);
                    cur.increment(add);
                    remaining -= add;
                    inv.markDirty();
                }
            }
        }
        // 占据空位阶段
        for (int i = 0; i <= 35 && remaining > 0; i++) {
            if (inv.getStack(i).isEmpty()) {
                int max = Math.min(variant.getMaxCount(), inv.getMaxCountPerStack());
                int put = Math.min(max, remaining);
                if (put > 0) {
                    ItemStack copy = variant.copy();
                    copy.setCount(put);
                    inv.setStack(i, copy);
                    remaining -= put;
                    inv.markDirty();
                }
            }
        }
        if (remaining > 0) {
            // 理论上不会发生，因为已经按容量扣减；兜底丢弃
            ItemStack drop = variant.copy();
            drop.setCount(remaining);
            player.dropItem(drop, false);
        }
    }

    private static void handleDepositCursor(ServerPlayerEntity serverPlayer, int button) {
        var cursor = serverPlayer.currentScreenHandler.getCursorStack();
        if (cursor.isEmpty()) return;
        if (button == 0) {
            // 新版：存入分离存储
            ItemStack remainder = insertIntoNewStorage(serverPlayer, cursor);
            serverPlayer.currentScreenHandler.setCursorStack(remainder);
        } else if (button == 1) {
            if (cursor.getCount() > 0) {
                ItemStack singleStack = cursor.copy();
                singleStack.setCount(1);
                ItemStack remainder = insertIntoNewStorage(serverPlayer, singleStack);
                if (remainder.isEmpty()) {
                    cursor.decrement(1);
                    serverPlayer.currentScreenHandler.setCursorStack(cursor);
                }
            }
        }
        serverPlayer.currentScreenHandler.sendContentUpdates();
        sendSync(serverPlayer);
    }

    private static void handleStorageDrop(ServerPlayerEntity player, int slotIndex, int amountType) {
        StorageInventory merged = buildMergedSnapshot(player);
        if (slotIndex < 0 || slotIndex >= merged.getCapacity()) return;
        ItemStack disp = merged.getDisplayStack(slotIndex);
        if (disp.isEmpty()) return;
        int want = amountType == 1 ? disp.getMaxCount() : 1;
        long got = takeFromMerged(player, disp, want);
        if (got <= 0) return;
        ItemStack drop = disp.copy();
        drop.setCount((int)Math.min(disp.getMaxCount(), got));
        dropItemTowardsLook(player, drop);
        sendSync(player);
    }
    public static StorageInventory buildMergedSnapshot(ServerPlayerEntity viewer) {
        var server = viewer.getServer();
        if (server == null) return new StorageInventory(0);
        
        // 获取共享的玩家UUID集合
        java.util.Set<java.util.UUID> sharedUuids = getSharedUuids(viewer);
        
        // 使用新版存储服务构建共享视图
        return NewStoreService.buildSharedView(server, viewer.getUuid(), sharedUuids);
    }
    
    /**
     * 获取共享仓库的玩家UUID集合
     */
    private static java.util.Set<java.util.UUID> getSharedUuids(ServerPlayerEntity viewer) {
        java.util.Set<java.util.UUID> sharedUuids = new java.util.LinkedHashSet<>();
        sharedUuids.add(viewer.getUuid());
        
        // 统计"我所依附的根拥有者集合"
        java.util.LinkedHashSet<java.util.UUID> rootOwners = new java.util.LinkedHashSet<>();
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(viewer);
        for (int i = 0; i < upgrades.getSlotCount(); i++) {
            ItemStack st = upgrades.getStack(i);
            if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.BARREL) {
                java.util.UUID owner = getOwnerUuidFromItem(st);
                if (owner != null && !owner.equals(viewer.getUuid())) {
                    rootOwners.add(owner);
                }
            }
        }
        
        // 如果未依附任何人，则自己就是根拥有者之一
        if (rootOwners.isEmpty()) {
            rootOwners.add(viewer.getUuid());
        }
        
        // 添加所有根拥有者
        sharedUuids.addAll(rootOwners);
        
        // 添加所有"同依附该根拥有者"的玩家
        var players = viewer.server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity p : players) {
            UpgradeInventory up = PlayerStorageService.getUpgradeInventory(p);
            boolean usesRoot = false;
            for (int i = 0; i < up.getSlotCount(); i++) {
                ItemStack st = up.getStack(i);
                if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.BARREL) {
                    java.util.UUID owner = getOwnerUuidFromItem(st);
                    if (owner != null && rootOwners.contains(owner)) { 
                        usesRoot = true; 
                        break; 
                    }
                }
            }
            if (usesRoot) {
                sharedUuids.add(p.getUuid());
            }
        }
        
        return sharedUuids;
    }

    private static long takeFromMerged(ServerPlayerEntity viewer, ItemStack variant, int want) {
        var server = viewer.getServer();
        if (server == null) return 0;
        
        // 获取共享的玩家UUID集合
        java.util.Set<java.util.UUID> sharedUuids = getSharedUuids(viewer);
        
        // 使用新版存储服务从共享视图中提取
        long got = NewStoreService.takeFromSharedView(server, viewer.getUuid(), sharedUuids, variant, want);
        
        // 取物后，向所有相关玩家广播更新（在线部分）
        broadcastToRelated(viewer);
        return got;
    }

    private static void broadcastToRelated(ServerPlayerEntity actor) {
        // 给自身与所有"同组根拥有者相关"的在线玩家发送同步
        var players = actor.server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity p : players) {
            sendIncrementalSyncOnDemand(p);
        }
    }

    /**
     * 获取仓库中指定物品的可用数量
     */
    public static long getAvailableCount(ServerPlayerEntity player, ItemStack variant) {
        if (variant.isEmpty()) {
            return 0;
        }
        
        // 使用合并的视图，确保与 takeFromStorage 一致
        StorageInventory merged = buildMergedSnapshot(player);
        long totalCount = 0;
        for (int i = 0; i < merged.getCapacity(); i++) {
            ItemStack disp = merged.getDisplayStack(i);
            if (!disp.isEmpty() && ItemStack.areItemsAndComponentsEqual(disp, variant)) {
                totalCount += merged.getCountByIndex(i);
            }
        }
        return totalCount;
    }

    /**
     * 从仓库取出指定数量的物品
     */
    public static long takeFromStorage(ServerPlayerEntity player, ItemStack variant, int want) {
        return takeFromMerged(player, variant, want);
    }

    /**
     * 处理 EMI 配方填充
     */
    private static void handleEmiRecipeFill(ServerPlayerEntity player, String recipeIdStr, int[] slotIndices, int[] itemCounts) {
        org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("Server handle EmiRecipeFill: player={}, recipeId={}, slots={}, counts={}", player.getName().getString(), recipeIdStr, java.util.Arrays.toString(slotIndices), java.util.Arrays.toString(itemCounts));
        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof net.minecraft.screen.CraftingScreenHandler) 
            && !(handler instanceof PortableCraftingScreenHandler)) {
            org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("EMI fill ignored: handler={} not crafting", handler.getClass().getName());
            return;
        }

        var id = net.minecraft.util.Identifier.tryParse(recipeIdStr);
        if (id == null) return;
        var entry = player.getServerWorld().getRecipeManager().get(id).orElse(null);
        if (entry == null || !(entry.value() instanceof net.minecraft.recipe.CraftingRecipe crafting)) return;

        // 展开配方输入为 3x3 Ingredient 数组
        net.minecraft.recipe.Ingredient[] needs = new net.minecraft.recipe.Ingredient[9];
        if (crafting instanceof net.minecraft.recipe.ShapedRecipe shaped) {
            int w = shaped.getWidth();
            int h = shaped.getHeight();
            var list = shaped.getIngredients();
            for (int i = 0; i < 9; i++) {
                int r = i / 3, c = i % 3;
                if (r < h && c < w) {
                    int idx = r * w + c;
                    needs[i] = idx < list.size() ? list.get(idx) : net.minecraft.recipe.Ingredient.EMPTY;
                } else needs[i] = net.minecraft.recipe.Ingredient.EMPTY;
            }
        } else {
            var list = crafting.getIngredients();
            for (int i = 0; i < 9; i++) {
                needs[i] = i < list.size() ? list.get(i) : net.minecraft.recipe.Ingredient.EMPTY;
            }
        }

        // 逐槽尝试填充：优先从合并仓库，其次玩家背包
        for (int i = 0; i < 9; i++) {
            var ing = needs[i];
            if (ing == null || ing.isEmpty()) continue;
            int slotIndex = 1 + i;
            Slot slot = handler.getSlot(slotIndex);
            if (!slot.getStack().isEmpty()) continue;

            ItemStack variant = findFirstMatchingInMerged(player, ing);
            if (!variant.isEmpty()) {
                long taken = takeFromMerged(player, variant, 1);
                if (taken > 0) {
                    ItemStack put = variant.copy();
                    put.setCount((int) taken);
                    slot.setStack(put);
                    continue;
                }
            }
            if (takeFromPlayerInventory(player, ing, 1)) {
                ItemStack any = pickAnyFromIngredient(ing);
                if (!any.isEmpty()) {
                    any.setCount(1);
                    slot.setStack(any);
                }
            }
        }

        handler.sendContentUpdates();
        sendSync(player);
        org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("Server EmiRecipeFill finished: synced storage state");
    }

    private static ItemStack findFirstMatchingInMerged(ServerPlayerEntity viewer, net.minecraft.recipe.Ingredient ing) {
        // 使用合并快照而不是单独的存储视图，确保包含新版仓库物品
        StorageInventory merged = buildMergedSnapshot(viewer);
        for (int i = 0; i < merged.getCapacity(); i++) {
            ItemStack disp = merged.getDisplayStack(i);
            if (!disp.isEmpty() && ing.test(disp)) {
                return disp;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean takeFromPlayerInventory(ServerPlayerEntity player, net.minecraft.recipe.Ingredient ing, int needed) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size() && needed > 0; i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && ing.test(st)) {
                st.decrement(1);
                if (st.isEmpty()) inv.setStack(i, ItemStack.EMPTY);
                needed--;
            }
        }
        return needed == 0;
    }

    private static ItemStack pickAnyFromIngredient(net.minecraft.recipe.Ingredient ing) {
        var stacks = ing.getMatchingStacks();
        return stacks.length > 0 ? stacks[0].copy() : ItemStack.EMPTY;
    }
    
    /**
     * 检查玩家是否有工作台升级且未禁用
     */
    private static boolean portableStorage$hasCraftingTableUpgrade(ServerPlayerEntity player) {
        // 检查仓库是否启用
        com.portable.storage.player.PlayerStorageAccess access = (com.portable.storage.player.PlayerStorageAccess) player;
        if (!access.portableStorage$isStorageEnabled()) {
            return false;
        }

        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        ScreenHandler handler = player.currentScreenHandler;

        // 确保在工作台界面
        if (!(handler instanceof net.minecraft.screen.CraftingScreenHandler)) return false;

        // 检查所有升级槽位是否有工作台且未禁用
        for (int i = 0; i < upgrades.getSlotCount(); i++) {
            ItemStack stack = upgrades.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.item.Items.CRAFTING_TABLE && !upgrades.isSlotDisabled(i, player)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 处理箱子升级移除时的扩展槽物品掉落
     */
	private static void handleChestUpgradeRemoval(ServerPlayerEntity player, UpgradeInventory upgrades) {
		// 获取所有扩展槽位中的物品
		java.util.List<ItemStack> extendedItems = upgrades.getExtendedSlotItems();

		if (!extendedItems.isEmpty()) {
			// 优先尝试放入玩家背包
			for (ItemStack item : extendedItems) {
				if (!player.getInventory().insertStack(item)) {
					// 背包满了，掉落到地上
					player.dropItem(item, false);
				}
			}

			// 清空扩展槽位
			upgrades.clearExtendedSlots();
		}
	}
	
	// 存储临时床方块的位置和相关信息
	private static final java.util.Map<net.minecraft.util.math.BlockPos, TempBedData> tempBeds = new java.util.concurrent.ConcurrentHashMap<>();
	
	// 临时床数据类
	private static class TempBedData {
		private final net.minecraft.block.BlockState originalState;
		private final long createTime;
		
		public TempBedData(net.minecraft.block.BlockState originalState, long createTime) {
			this.originalState = originalState;
			this.createTime = createTime;
		}
		
		public net.minecraft.block.BlockState getOriginalState() {
			return originalState;
		}
		
		public long getCreateTime() {
			return createTime;
		}
	}
	
	private static void handleBedUpgradeSleep(ServerPlayerEntity player) {
        // 裂隙内禁止使用床升级原地睡觉
        if (player.getWorld().getRegistryKey() == com.portable.storage.world.SpaceRiftManager.DIMENSION_KEY) {
            player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_sleep_in_rift"), true);
            return;
        }
		// 检查是否在白天
		if (player.getWorld().isDay()) {
			// 白天不能睡觉，发送消息
			player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_sleep"), true);
			return;
		}
		
		// 检查玩家脚下是否有方块
		if (player.getWorld().getBlockState(player.getBlockPos().down()).isAir()) {
			// 不在安全位置，发送消息
			player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_safe_place"), true);
			return;
		}
		
		net.minecraft.util.math.BlockPos bedPos = player.getBlockPos();
		net.minecraft.util.math.Direction facing = player.getHorizontalFacing();
		net.minecraft.util.math.BlockPos footPos = bedPos.offset(facing.getOpposite());

		// 若脚下或计划放置的脚部位置已经是床，则拒绝放置
		net.minecraft.block.BlockState curState = player.getWorld().getBlockState(bedPos);
		net.minecraft.block.BlockState footCurState = player.getWorld().getBlockState(footPos);
		if (curState.getBlock() instanceof net.minecraft.block.BedBlock || footCurState.getBlock() instanceof net.minecraft.block.BedBlock) {
			player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_safe_place"), true);
			return;
		}
		
		// 检查该位置是否已经有临时床
		if (tempBeds.containsKey(bedPos) || tempBeds.containsKey(footPos)) {
			player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_safe_place"), true);
			return;
		}
		
		// 检查脚部位置是否可用
		if (!player.getWorld().getBlockState(footPos).isAir()) {
			player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_safe_place"), true);
			return;
		}
		
		// 存储原始方块状态和创建时间
		net.minecraft.block.BlockState headOriginalState = player.getWorld().getBlockState(bedPos);
		net.minecraft.block.BlockState footOriginalState = player.getWorld().getBlockState(footPos);
		
		// 创建完整的床方块状态（使用自定义临时床方块）
		net.minecraft.block.BlockState headBedState = ModBlocks.TEMP_BED.getDefaultState()
			.with(net.minecraft.block.BedBlock.FACING, facing)
			.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD)
			.with(net.minecraft.block.BedBlock.OCCUPIED, false);
			
		net.minecraft.block.BlockState footBedState = ModBlocks.TEMP_BED.getDefaultState()
			.with(net.minecraft.block.BedBlock.FACING, facing)
			.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.FOOT)
			.with(net.minecraft.block.BedBlock.OCCUPIED, false);
		
		// 存储两个位置的原始状态
		TempBedData headBedData = new TempBedData(headOriginalState, player.getWorld().getTime());
		TempBedData footBedData = new TempBedData(footOriginalState, player.getWorld().getTime());
		tempBeds.put(bedPos, headBedData);
		tempBeds.put(footPos, footBedData);
		
		// 放置完整的床方块
		player.getWorld().setBlockState(bedPos, headBedState);
		player.getWorld().setBlockState(footPos, footBedState);
		
		// 尝试让玩家自动睡觉
		try {
			// 模拟玩家右键点击床的行为
			net.minecraft.block.BlockState bedState = player.getWorld().getBlockState(bedPos);
			if (bedState.getBlock() instanceof net.minecraft.block.BedBlock) {
				// 使用床方块的onUse方法来触发睡觉
				net.minecraft.util.hit.BlockHitResult hitResult = new net.minecraft.util.hit.BlockHitResult(
					new net.minecraft.util.math.Vec3d(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5),
					net.minecraft.util.math.Direction.UP, bedPos, false
				);
				bedState.onUse(player.getWorld(), player, hitResult);
				// 如果睡觉成功，发送消息
				player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed_placed"), true);
			} else {
				// 如果床方块状态不正确，清理临时床
				cleanupCompleteTempBed(bedPos, player.getWorld());
				player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_safe_place"), true);
			}
		} catch (Exception e) {
			// 如果睡觉失败，清理临时床并发送错误消息
			cleanupCompleteTempBed(bedPos, player.getWorld());
			player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".bed.no_safe_place"), true);
		}
	}
	
	/**
	 * 清理临时床方块
	 */
	public static void cleanupTempBed(net.minecraft.util.math.BlockPos pos, net.minecraft.world.World world) {
		TempBedData bedData = tempBeds.remove(pos);
		if (bedData != null) {
			world.setBlockState(pos, bedData.getOriginalState());
		}
	}
	
	/**
	 * 清理完整的临时床（头部和脚部）
	 */
	public static void cleanupCompleteTempBed(net.minecraft.util.math.BlockPos headPos, net.minecraft.world.World world) {
		// 清理头部
		cleanupTempBed(headPos, world);
		
		// 查找并清理脚部
		net.minecraft.block.BlockState headState = world.getBlockState(headPos);
		if (headState.getBlock() instanceof net.minecraft.block.BedBlock) {
			net.minecraft.util.math.Direction facing = headState.get(net.minecraft.block.BedBlock.FACING);
			net.minecraft.util.math.BlockPos footPos = headPos.offset(facing.getOpposite());
			cleanupTempBed(footPos, world);
		}
	}
	
	/**
	 * 获取临时床方块的原始状态
	 */
	public static net.minecraft.block.BlockState getOriginalState(net.minecraft.util.math.BlockPos pos) {
		TempBedData bedData = tempBeds.get(pos);
		return bedData != null ? bedData.getOriginalState() : null;
	}
	
	/**
	 * 移除临时床记录
	 */
	public static void removeTempBed(net.minecraft.util.math.BlockPos pos) {
		tempBeds.remove(pos);
	}
	
	/**
	 * 检查是否是临时床
	 */
	public static boolean isTempBed(net.minecraft.util.math.BlockPos pos) {
		return tempBeds.containsKey(pos);
	}
	
	/**
	 * 获取所有临时床位置
	 */
	public static java.util.Set<net.minecraft.util.math.BlockPos> getTempBedPositions() {
		return tempBeds.keySet();
	}

	// ===== 覆盖式合成：从仓库补满指定输入槽 =====
	private static void refillCraftingFromStorage(ServerPlayerEntity player, int slotIndex, ItemStack targetStack) {
		if (targetStack == null || targetStack.isEmpty()) return;
		ScreenHandler handler = player.currentScreenHandler;
		boolean allowed;
		if (handler instanceof PortableCraftingScreenHandler) {
			allowed = true;
		} else if (handler instanceof net.minecraft.screen.CraftingScreenHandler) {
			allowed = portableStorage$hasCraftingTableUpgrade(player);
		} else if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
			allowed = true;
		} else {
			allowed = false;
		}
		if (!allowed) return;
		if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;
		Slot slot = handler.getSlot(slotIndex);
		ItemStack current = slot.getStack();
		if (!current.isEmpty() && !ItemStack.areItemsAndComponentsEqual(current, targetStack)) {
			return;
		}
		int maxPerSlot = Math.min(targetStack.getMaxCount(), player.getInventory().getMaxCountPerStack());
		java.util.ArrayList<Integer> group = new java.util.ArrayList<>();
		group.add(slotIndex);
		for (int i = 1; i < Math.min(10, handler.slots.size()); i++) {
			if (i == slotIndex) continue;
			Slot s = handler.getSlot(i);
			ItemStack st = s.getStack();
			if (!st.isEmpty() && ItemStack.areItemsAndComponentsEqual(st, targetStack)) {
				group.add(i);
			}
		}
		int[] needBySlot = new int[group.size()];
		int totalNeed = 0;
		for (int gi = 0; gi < group.size(); gi++) {
			Slot gs = handler.getSlot(group.get(gi));
			ItemStack cs = gs.getStack();
			int curCnt = cs.isEmpty() ? 0 : cs.getCount();
			int need = Math.max(0, maxPerSlot - curCnt);
			needBySlot[gi] = need;
			totalNeed += need;
		}
		if (totalNeed == 0 && current.isEmpty()) {
			int need = Math.max(0, Math.min(maxPerSlot, targetStack.getMaxCount()));
			needBySlot[0] = need;
			totalNeed = need;
		}
		if (totalNeed <= 0) return;
		long got = takeFromMerged(player, targetStack, totalNeed);
		if (got <= 0) return;
		int[] alloc = new int[group.size()];
		int remain = (int) got;
		while (remain > 0) {
			boolean progressed = false;
			for (int gi = 0; gi < group.size() && remain > 0; gi++) {
				int room = needBySlot[gi] - alloc[gi];
				if (room > 0) {
					alloc[gi] += 1;
					remain -= 1;
					progressed = true;
				}
			}
			if (!progressed) break;
		}
		for (int gi = 0; gi < group.size(); gi++) {
			int idx = group.get(gi);
			int add = alloc[gi];
			if (add <= 0) continue;
			Slot gs = handler.getSlot(idx);
			ItemStack cs = gs.getStack();
			if (cs.isEmpty()) {
				ItemStack put = targetStack.copy();
				put.setCount(add);
				gs.setStack(put);
			} else {
				cs.increment(add);
			}
			gs.markDirty();
		}
        handler.sendContentUpdates();
        sendSync(player);
	}
	
	/**
	 * 检查临时床是否已过期（10秒未交互）
	 */
	public static boolean isTempBedExpired(net.minecraft.util.math.BlockPos pos, long currentTime) {
		TempBedData bedData = tempBeds.get(pos);
		if (bedData == null) {
			return false;
		}
		
		long createTime = bedData.getCreateTime();
		return (currentTime - createTime) >= 200; // 10秒 = 200 ticks
	}
}
