package com.portable.storage.net;

import com.portable.storage.config.ServerConfig;
import com.portable.storage.net.payload.*;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.player.StoragePersistence;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.sync.StorageSyncManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public final class ServerNetworkingHandlers {
	private ServerNetworkingHandlers() {}
    // 附魔之瓶升级：循环的存取等级索引，简单存内存，不做持久化
    private static final java.util.Map<java.util.UUID, Integer> xpStepIndexByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int[] XP_STEPS = new int[] {1, 5, 10, 100};
	
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
		ServerPlayNetworking.registerGlobalReceiver(RequestSyncC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				// 标记玩家开始查看仓库界面
				com.portable.storage.sync.PlayerViewState.startViewing(player.getUuid());
				
				// 处理积攒的变化
				com.portable.storage.sync.ChangeAccumulator.processPlayerChanges(player);
				
				// 总是发送启用状态同步，让客户端知道当前状态
				sendEnablementSync(player);
				// 只有启用时才发送其他同步
				if (!checkAndRejectIfNotEnabled(player)) {
					sendIncrementalSync(player);
				}
			});
		});
		
		// 注册同步确认处理器
		ServerPlayNetworking.registerGlobalReceiver(SyncAckC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				StorageSyncManager.handleSyncAck(player.getUuid(), payload.syncId(), payload.success());
			});
		});

        ServerPlayNetworking.registerGlobalReceiver(StorageClickC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.index();
			context.server().execute(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
                StorageInventory merged = buildMergedSnapshot(player);
                if (slotIndex < 0 || slotIndex >= merged.getCapacity()) return;
                ItemStack disp = merged.getDisplayStack(slotIndex);
				if (disp.isEmpty()) return;
				// 旧包：左键全取一组
				long want = disp.getMaxCount();
                long got = takeFromMerged(player, disp, (int) want);
                ItemStack moved = disp.copy();
                moved.setCount((int)Math.min(disp.getMaxCount(), got));
                insertIntoPlayerInventory(player, moved);
                sendIncrementalSyncOnDemand(player);
			});
		});

		// Shift 直接取物：左键一组，右键一个
		ServerPlayNetworking.registerGlobalReceiver(StorageShiftTakeC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.index();
			int button = payload.button();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				StorageInventory merged = buildMergedSnapshot(player);
				if (slotIndex < 0 || slotIndex >= merged.getCapacity()) return;
				ItemStack disp = merged.getDisplayStack(slotIndex);
				if (disp.isEmpty()) return;

				int want = (button == 1) ? 1 : disp.getMaxCount();
				long got = takeFromMerged(player, disp, want);
				if (got <= 0) return;
				ItemStack moved = disp.copy();
				moved.setCount((int)Math.min(disp.getMaxCount(), got));
				insertIntoPlayerInventory(player, moved);
				sendIncrementalSync(player);
			});
		});

		// 统一槽位点击，遵循原版语义
		ServerPlayNetworking.registerGlobalReceiver(StorageSlotClickC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.index();
			int button = payload.button(); // 0 左键，1 右键
            context.server().execute(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
                StorageInventory merged = buildMergedSnapshot(player);
                if (slotIndex < 0 || slotIndex >= merged.getCapacity()) return;
                ItemStack slotStack = merged.getDisplayStack(slotIndex);
				ItemStack cursor = player.currentScreenHandler.getCursorStack();

				if (cursor.isEmpty()) {
					// 拿取：左键全拿，右键半拿（上取整，最大32）
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
					// 放入：左键全部，右键一个；均采用全局堆叠策略（同类合并→空槽），与左键行为保持一致
                    if (button == 0) {
                        StorageInventory self = PlayerStorageService.getInventory(player);
                        ItemStack remainder = insertIntoStorage(self, cursor);
						player.currentScreenHandler.setCursorStack(remainder);
                    } else {
                        if (cursor.getCount() > 0) {
                            StorageInventory self = PlayerStorageService.getInventory(player);
                            ItemStack singleStack = cursor.copy();
                            singleStack.setCount(1);
                            ItemStack remainder = insertIntoStorage(self, singleStack);
							if (remainder.isEmpty()) {
								cursor.decrement(1);
								player.currentScreenHandler.setCursorStack(cursor);
							}
						}
					}
				}

				player.currentScreenHandler.sendContentUpdates();
				sendIncrementalSync(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DepositSlotC2SPayload.ID, (payload, context) -> {
			int handlerSlotId = payload.handlerSlotId();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				ScreenHandler sh = player.currentScreenHandler;
				if (handlerSlotId < 0 || handlerSlotId >= sh.slots.size()) return;
				Slot slot = sh.getSlot(handlerSlotId);
				ItemStack from = slot.getStack();
				if (from.isEmpty()) return;

				StorageInventory inv = PlayerStorageService.getInventory(player);
                ItemStack remainder = insertIntoStorage(inv, from);
                slot.setStack(remainder);
				slot.markDirty();
				sh.sendContentUpdates();
				sendIncrementalSync(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DepositCursorC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				var serverPlayer = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(serverPlayer)) return;
				var cursor = serverPlayer.currentScreenHandler.getCursorStack();
				if (cursor.isEmpty()) return;
				
				int button = payload.button();
				StorageInventory inv = PlayerStorageService.getInventory(serverPlayer);
				
				if (button == 0) {
					// 左键：存入全部物品
					ItemStack remainder = insertIntoStorage(inv, cursor);
					serverPlayer.currentScreenHandler.setCursorStack(remainder);
				} else if (button == 1) {
					// 右键：只存入一个物品
					if (cursor.getCount() > 0) {
						ItemStack singleStack = cursor.copy();
						singleStack.setCount(1);
						ItemStack remainder = insertIntoStorage(inv, singleStack);
						if (remainder.isEmpty()) {
							cursor.decrement(1);
							serverPlayer.currentScreenHandler.setCursorStack(cursor);
						}
					}
				}
				
				serverPlayer.currentScreenHandler.sendContentUpdates();
				sendIncrementalSync(serverPlayer);
			});
		});

		// 从仓库直接丢出悬停物品（Q/CTRL+Q）
		ServerPlayNetworking.registerGlobalReceiver(StorageDropC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.index();
			int amountType = payload.amountType(); // 0 一个；1 一组
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
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
				sendIncrementalSync(player);
			});
		});


		ServerPlayNetworking.registerGlobalReceiver(RefillCraftingC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.slotIndex();
			ItemStack targetStack = payload.targetStack();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;

				ScreenHandler handler = player.currentScreenHandler;
				// 允许的容器：
				// - PortableCraftingScreenHandler：总是允许（已启用仓库即可）
				// - CraftingScreenHandler（原版3x3）：需要工作台升级
				// - PlayerScreenHandler（背包2x2）：允许
				boolean allowed;
				if (handler instanceof com.portable.storage.screen.PortableCraftingScreenHandler) {
					allowed = true;
				} else if (handler instanceof net.minecraft.screen.CraftingScreenHandler) {
					allowed = portableStorage$hasCraftingTableUpgrade(player);
				} else if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
					allowed = true;
				} else {
					allowed = false;
				}
				if (!allowed) return;

				// 验证槽位索引
				if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;
				Slot slot = handler.getSlot(slotIndex);
				
				// 获取当前槽位物品
				ItemStack current = slot.getStack();
				
				// 检查物品类型是否匹配（如果槽位不为空）
				if (!current.isEmpty() && !ItemStack.areItemsAndComponentsEqual(current, targetStack)) {
					// 槽位物品已经变了，不补充
					return;
				}

				// 计算每槽上限
				int maxPerSlot = Math.min(targetStack.getMaxCount(), player.getInventory().getMaxCountPerStack());

				// 组装同类物品分组（保持摆放形状）：包括当前槽，以及其它与目标物品“完全一致”的非空槽
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

				// 计算各槽缺口
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
				// 若组内无缺口，但当前槽为空（仅当前槽需要），按目标计
				if (totalNeed == 0 && current.isEmpty()) {
					int need = Math.max(0, Math.min(maxPerSlot, targetStack.getMaxCount()));
					needBySlot[0] = need;
					totalNeed = need;
				}
				if (totalNeed <= 0) return;

				// 统一从合并视图提取 totalNeed 个目标物品
				long got = takeFromMerged(player, targetStack, totalNeed);
				if (got <= 0) return;

				// 使用轮询（round-robin）方式均衡分配，确保尽可能平均
				int[] alloc = new int[group.size()];
				int remain = (int)got;
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
					if (!progressed) break; // 所有槽位都满或没有缺口
				}

				// 写回各槽
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
				sendIncrementalSync(player);
			});
		});

		// 升级槽位点击
		ServerPlayNetworking.registerGlobalReceiver(UpgradeSlotClickC2SPayload.ID, (payload, context) -> {
			int slot = payload.slot();
			int button = payload.button();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);

				if (slot < 0 || slot >= upgrades.getSlotCount()) return;

				// 扩展槽位检查特定操作
                if (com.portable.storage.storage.UpgradeInventory.isExtendedSlot(slot)) {
                    // 槽位5（光灵箭）、槽位6（床）、槽位7（附魔之瓶）可以接受操作
                    if (slot != 5 && slot != 6 && slot != 7) {
						return;
					}
				}

				// 右键点击处理
                if (button == 1) {
					// 床升级槽位右键睡觉
                    if (slot == 6 && upgrades.isBedUpgradeActive()) {
						handleBedUpgradeSleep(player);
						return;
					}
                    // 附魔之瓶升级：右键循环存取等级
                    if (slot == 7 && !upgrades.isSlotDisabled(7) && !upgrades.getStack(7).isEmpty()) {
                        int idx = xpStepIndexByPlayer.getOrDefault(player.getUuid(), 0);
                        idx = (idx + 1) % XP_STEPS.length;
                        xpStepIndexByPlayer.put(player.getUuid(), idx);
                        int step = XP_STEPS[idx];
                        player.sendMessage(net.minecraft.text.Text.translatable("portable_storage.exp_bottle.step", step), true);
                        // 同步XP步长到客户端
                        ServerPlayNetworking.send(player, new com.portable.storage.net.payload.XpStepSyncS2CPayload(idx));
                        return;
                    }
					// 其他槽位切换禁用状态
					upgrades.toggleSlotDisabled(slot);
					sendIncrementalSync(player);
					return;
				}
				
				ItemStack cursor = player.currentScreenHandler.getCursorStack();
				ItemStack slotStack = upgrades.getStack(slot);

				// Barrel 绑定与规则校验
				if (!cursor.isEmpty() && cursor.getItem() == net.minecraft.item.Items.BARREL && cursor.getCount() == 1) {
					// 如果目标槽位为空且是木桶槽位
					if (slotStack.isEmpty() && slot == 3) { // 槽位3是木桶
						// 链式/循环共享约束：
						java.util.UUID ownerUuidFromItem = getOwnerUuidFromItem(cursor);
						java.util.UUID self = player.getUuid();
						if (ownerUuidFromItem != null && !ownerUuidFromItem.equals(self)) {
							// 当前玩家若已作为“从属”（已使用他人绑定木桶），禁止再插入任何绑定木桶（阻止循环/多重从属）
							if (isPlayerDependent(player)) {
								return; // 拒绝插入
							}
							// 绑定拥有者如果本身已作为从属，禁止其他人再使用其绑定木桶（阻止链式共享）
							ServerPlayerEntity ownerOnline = player.server.getPlayerManager().getPlayer(ownerUuidFromItem);
							if (ownerOnline != null && isPlayerDependent(ownerOnline)) {
								return; // 拒绝插入
							}
						}

						// 若未有绑定信息则写入玩家 UUID（使用 DataComponents CUSTOM_DATA 储存）
						net.minecraft.nbt.NbtCompound custom = getOrCreateCustom(cursor);
						if (!custom.contains("ps_owner_uuid_most")) {
							java.util.UUID uuid = player.getUuid();
							custom.putLong("ps_owner_uuid_most", uuid.getMostSignificantBits());
							custom.putLong("ps_owner_uuid_least", uuid.getLeastSignificantBits());
							String name = player.getGameProfile() != null ? player.getGameProfile().getName() : player.getName().getString();
							custom.putString("ps_owner_name", name);
							cursor.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(custom));
							// 附魔光效（物品）
							cursor.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

							// 同步写入方块实体数据，确保放置后不会丢失
							net.minecraft.nbt.NbtCompound be = getOrCreateBlockEntityData(cursor);
							// 1.21 的 BLOCK_ENTITY_DATA 在编码时需要包含方块实体 id
							if (!be.contains("id")) {
								be.putString("id", "minecraft:barrel");
							}
							be.putLong("ps_owner_uuid_most", custom.getLong("ps_owner_uuid_most"));
							be.putLong("ps_owner_uuid_least", custom.getLong("ps_owner_uuid_least"));
							be.putString("ps_owner_name", custom.getString("ps_owner_name"));
							cursor.set(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA, net.minecraft.component.type.NbtComponent.of(be));
						}
					}
				}
				
				if (cursor.isEmpty()) {
					// 手持空，取出槽位物品
					if (!slotStack.isEmpty()) {
						ItemStack taken = upgrades.takeStack(slot);
						
						// 如果是箱子升级被取出，需要处理扩展槽物品的掉落
						if (slot == 2 && taken.getItem() == net.minecraft.item.Items.CHEST) {
							handleChestUpgradeRemoval(player, upgrades);
						}
						
						player.currentScreenHandler.setCursorStack(taken);
						player.currentScreenHandler.sendContentUpdates();
						sendIncrementalSync(player);
					}
				} else {
					// 手持物品
					if (slotStack.isEmpty()) {
						// 槽位为空，尝试放入
						if (cursor.getCount() == 1) {
							if (upgrades.tryInsert(slot, cursor)) {
								player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
								player.currentScreenHandler.sendContentUpdates();
								sendIncrementalSync(player);
							}
						}
					} else {
						// 槽位有物品，交换
						if (cursor.getCount() == 1) {
							ItemStack taken = upgrades.takeStack(slot);
							if (upgrades.tryInsert(slot, cursor)) {
								player.currentScreenHandler.setCursorStack(taken);
								player.currentScreenHandler.sendContentUpdates();
								sendIncrementalSync(player);
							} else {
								// 放入失败，恢复
								upgrades.setStack(slot, taken);
							}
						}
					}
				}
			});
		});
		
		// EMI 配方填充处理
		ServerPlayNetworking.registerGlobalReceiver(EmiRecipeFillC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("Server received EmiRecipeFill payload: recipeId={}", payload.recipeId());
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				boolean hasUpgrade = portableStorage$hasCraftingTableUpgrade(player);
				ScreenHandler handler = player.currentScreenHandler;
				if (!(handler instanceof net.minecraft.screen.CraftingScreenHandler) &&
					!(handler instanceof com.portable.storage.screen.PortableCraftingScreenHandler)) {
					org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("EMI fill ignored at receiver: handler={} not crafting", handler.getClass().getName());
					return;
				}
				// 仅当是原版工作台容器时强制要求升级；自定义容器直接允许
				if (handler instanceof net.minecraft.screen.CraftingScreenHandler && !hasUpgrade) {
					org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("EMI fill ignored at receiver: no upgrade present for vanilla crafting handler");
					return;
				}
				
				// 处理配方填充
				handleEmiRecipeFill(player, payload);
			});
		});

		// 虚拟“瓶装经验”点击：button=0 左键取出，1 右键存入
		ServerPlayNetworking.registerGlobalReceiver(com.portable.storage.net.payload.XpBottleClickC2SPayload.ID, (payload, context) -> {
			int button = payload.button();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				// 必须有附魔之瓶升级且未禁用
				if (upgrades.isSlotDisabled(7) || upgrades.getStack(7).isEmpty()) return;
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
							player.sendMessage(Text.translatable("portable_storage.exp_bottle.delta", "+" + actualWithdrawn), true);
						}
					} else if (availableXp > 0) {
						// 经验不足，取出全部剩余经验
						long taken = upgrades.removeFromXpPool(availableXp);
						if (taken > 0) {
							// 直接增加经验值，不按等级提升
							player.addExperience((int)Math.min(Integer.MAX_VALUE, taken));
							player.sendMessage(Text.translatable("portable_storage.exp_bottle.delta", "+" + taken), true);
						}
					}
					sendUpgradeSync(player);
				} else {
					// 右键：从玩家扣除经验，存入"仓库XP池"
					// 使用新的方法扣除玩家经验，避免残留
					int deposited = depositPlayerXpByLevels(player, levels);
					if (deposited > 0) {
						upgrades.addToXpPool(deposited);
						player.sendMessage(Text.translatable("portable_storage.exp_bottle.delta", "-" + deposited), true);
					}
					sendUpgradeSync(player);
				}
			});
		});

		// 附魔之瓶等级维持切换
		ServerPlayNetworking.registerGlobalReceiver(com.portable.storage.net.payload.XpBottleMaintenanceToggleC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				if (checkAndRejectIfNotEnabled(player)) return;
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
				// 必须有附魔之瓶升级且未禁用
				if (upgrades.isSlotDisabled(7) || upgrades.getStack(7).isEmpty()) return;
				
				// 切换等级维持状态
				upgrades.toggleLevelMaintenance();
				boolean enabled = upgrades.isLevelMaintenanceEnabled();
				Text status = enabled ? 
					Text.translatable("portable_storage.toggle.enabled") : 
					Text.translatable("portable_storage.toggle.disabled");
				player.sendMessage(Text.translatable("portable_storage.exp_bottle.maintenance_toggle", status), true);
				sendUpgradeSync(player);
			});
		});

        // 切回原版工作台：确保关闭旧容器并清理自定义容器的输入/输出槽位，避免残留状态影响原版合成
        ServerPlayNetworking.registerGlobalReceiver(com.portable.storage.net.payload.RequestVanillaCraftingOpenC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
                // 读取原始方块上下文（如果当前是自定义工作台），优先使用该上下文打开原版
                final net.minecraft.util.math.BlockPos[] openPosHolder = new net.minecraft.util.math.BlockPos[1];
                final net.minecraft.world.World[] openWorldHolder = new net.minecraft.world.World[1];
                if (player.currentScreenHandler instanceof com.portable.storage.screen.PortableCraftingScreenHandler pch) {
                    // 先清空自定义工作台中的输入物品到玩家背包（仅1..9，保留输出槽0）
                    for (int i = 1; i <= 9 && i < pch.slots.size(); i++) {
                        net.minecraft.screen.slot.Slot slot = pch.getSlot(i);
                        ItemStack st = slot.getStack();
                        if (!st.isEmpty()) {
                            ItemStack copy = st.copy();
                            slot.setStack(ItemStack.EMPTY);
                            insertIntoPlayerInventory(player, copy);
                        }
                    }
                    // 从自定义 handler 的上下文读取世界与方块位置
                    pch.getContext().run((w, pos) -> {
                        openWorldHolder[0] = w;
                        openPosHolder[0] = pos;
                    });
                }
                // 显式关闭当前容器，防止旧 handler 残留状态
                player.closeHandledScreen();
                // 直接打开原版工作台容器（优先使用原上下文，否则退化为玩家当前位置）
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
			});
		});

	}

	private static net.minecraft.nbt.NbtCompound getOrCreateCustom(net.minecraft.item.ItemStack stack) {
		net.minecraft.component.type.NbtComponent comp = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
		return comp != null ? comp.copyNbt() : new net.minecraft.nbt.NbtCompound();
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

	private static int currentTotalXp(ServerPlayerEntity player) {
		int level = player.experienceLevel;
		float progress = player.experienceProgress;
		int base = totalXpForLevel(level);
		int next = xpToNextLevel(level);
		return Math.max(0, base + (int)Math.floor(next * Math.max(0f, Math.min(1f, progress))));
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

	private static net.minecraft.nbt.NbtCompound getOrCreateBlockEntityData(net.minecraft.item.ItemStack stack) {
		net.minecraft.component.type.NbtComponent comp = stack.get(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA);
		return comp != null ? comp.copyNbt() : new net.minecraft.nbt.NbtCompound();
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

	private static boolean isPlayerDependent(ServerPlayerEntity player) {
		UpgradeInventory inv = PlayerStorageService.getUpgradeInventory(player);
		java.util.UUID self = player.getUuid();
		for (int i = 0; i < inv.getSlotCount(); i++) {
			net.minecraft.item.ItemStack st = inv.getStack(i);
			if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.BARREL) {
				java.util.UUID owner = getOwnerUuidFromItem(st);
				if (owner != null && !owner.equals(self)) {
					return true;
				}
			}
		}
		return false;
	}

    private static void insertIntoPlayerInventory(ServerPlayerEntity player, ItemStack stack) {
		Inventory inv = player.getInventory();
		if (stack.isEmpty()) return;
		// 先尝试合并
		for (int i = 0; i < inv.size(); i++) {
			ItemStack cur = inv.getStack(i);
			if (!cur.isEmpty() && ItemStack.areItemsAndComponentsEqual(cur, stack)) {
				int max = Math.min(cur.getMaxCount(), inv.getMaxCountPerStack());
				int can = Math.min(stack.getCount(), max - cur.getCount());
				if (can > 0) {
					cur.increment(can);
					stack.decrement(can);
					inv.markDirty();
					if (stack.isEmpty()) return;
				}
			}
		}
		// 放到空位
		for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
			if (inv.getStack(i).isEmpty()) {
				inv.setStack(i, stack.copy());
				stack.setCount(0);
				inv.markDirty();
				break;
			}
		}
	}

    private static ItemStack insertIntoStorage(StorageInventory storage, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        //通过插入整个 ItemStack 变体来保留完整组件
        storage.insertItemStack(stack.copy(), System.currentTimeMillis());
        return ItemStack.EMPTY;
    }

    public static void sendSync(ServerPlayerEntity player) {
        StorageInventory merged = buildMergedSnapshot(player);
        NbtCompound nbt = new NbtCompound();
        // 使用玩家注册表上下文，确保附魔等基于注册表的数据正确序列化
        merged.writeNbt(nbt, player.getRegistryManager());
        ServerPlayNetworking.send(player, new StorageSyncS2CPayload(nbt));
		sendUpgradeSync(player);
		sendEnablementSync(player);
	}
	
	/**
	 * 发送增量同步（异步）
	 */
	public static void sendIncrementalSync(ServerPlayerEntity player) {
		StorageInventory merged = buildMergedSnapshot(player);
		
		// 检查是否启用增量同步
		if (ServerConfig.getInstance().isEnableIncrementalSync()) {
			StorageSyncManager.sendIncrementalSync(player, merged);
		} else {
			// 回退到传统全量同步
			sendSync(player);
			return;
		}
		
		sendUpgradeSync(player);
		sendEnablementSync(player);
	}
	
	/**
	 * 发送按需增量同步（只对正在查看的玩家发送）
	 */
	public static void sendIncrementalSyncOnDemand(ServerPlayerEntity player) {
		StorageInventory merged = buildMergedSnapshot(player);
		
		// 使用按需同步
		StorageSyncManager.sendIncrementalSyncOnDemand(player, merged);
		
		sendUpgradeSync(player);
		sendEnablementSync(player);
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
		ServerPlayNetworking.send(player, new UpgradeSyncS2CPayload(nbt));
		// 同时同步XP步长
		int xpStep = xpStepIndexByPlayer.getOrDefault(player.getUuid(), 0);
		ServerPlayNetworking.send(player, new com.portable.storage.net.payload.XpStepSyncS2CPayload(xpStep));
	}
	
	public static void sendEnablementSync(ServerPlayerEntity player) {
		PlayerStorageAccess access = (PlayerStorageAccess) player;
		boolean enabled = access.portableStorage$isStorageEnabled();
		ServerPlayNetworking.send(player, new StorageEnablementSyncS2CPayload(enabled));
	}

    // ===== 合并仓库（共享木桶） =====
    private static java.util.List<StorageInventory> getViewStorages(ServerPlayerEntity viewer) {
        java.util.List<StorageInventory> list = new java.util.ArrayList<>();
        java.util.LinkedHashSet<java.util.UUID> added = new java.util.LinkedHashSet<>();
        // 自己优先
        list.add(PlayerStorageService.getInventory(viewer));
        added.add(viewer.getUuid());

        // 统计“我所依附的根拥有者集合”
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

        // 如果未依附任何人，则自己就是根拥有者之一（用于处理“我是源头”，让使用我木桶的人加入）
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

            // 加入所有“同依附该根拥有者”的玩家（包括 viewer 自己，已去重）
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

    public static StorageInventory buildMergedSnapshot(ServerPlayerEntity viewer) {
        StorageInventory agg = new StorageInventory(0);
        for (StorageInventory s : getViewStorages(viewer)) {
            for (int i = 0; i < s.getCapacity(); i++) {
                ItemStack disp = s.getDisplayStack(i);
                if (disp.isEmpty()) continue;
                long cnt = s.getCountByIndex(i);
                if (cnt <= 0) continue;
                long left = cnt;
                while (left > 0) {
                    int chunk = (int)Math.min(Integer.MAX_VALUE, left);
                    ItemStack copy = disp.copy();
                    copy.setCount(chunk);
                    // 保留原来的时间戳，不使用当前时间
                    agg.insertItemStackWithOriginalTimestamp(copy, s.getTimestampByIndex(i));
                    left -= chunk;
                }
            }
        }
        return agg;
    }

    private static long takeFromMerged(ServerPlayerEntity viewer, ItemStack variant, int want) {
        long remaining = want;
        long got = 0;
        for (StorageInventory s : getViewStorages(viewer)) {
            if (remaining <= 0) break;
            for (int i = 0; i < s.getCapacity() && remaining > 0; i++) {
                ItemStack disp = s.getDisplayStack(i);
                if (disp.isEmpty()) continue;
                if (ItemStack.areItemsAndComponentsEqual(disp, variant)) {
                    long can = Math.min(remaining, s.getCountByIndex(i));
                    if (can > 0) {
                        long t = s.takeByIndex(i, can, System.currentTimeMillis());
                        got += t;
                        remaining -= t;
                    }
                }
            }
        }
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
     * 处理 EMI 配方填充
     */
    private static void handleEmiRecipeFill(ServerPlayerEntity player, EmiRecipeFillC2SPayload payload) {
        org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("Server handle EmiRecipeFill: player={}, recipeId={}, slots={}, counts={}", player.getName().getString(), payload.recipeId(), java.util.Arrays.toString(payload.slotIndices()), java.util.Arrays.toString(payload.itemCounts()));
        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof net.minecraft.screen.CraftingScreenHandler) 
            && !(handler instanceof com.portable.storage.screen.PortableCraftingScreenHandler)) {
            org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("EMI fill ignored: handler={} not crafting", handler.getClass().getName());
            return;
        }

        var id = net.minecraft.util.Identifier.tryParse(payload.recipeId());
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
        sendIncrementalSyncOnDemand(player);
        org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("Server EmiRecipeFill finished: synced storage state");
    }

    private static ItemStack findFirstMatchingInMerged(ServerPlayerEntity viewer, net.minecraft.recipe.Ingredient ing) {
        for (StorageInventory s : getViewStorages(viewer)) {
            for (int i = 0; i < s.getCapacity(); i++) {
                ItemStack disp = s.getDisplayStack(i);
                if (!disp.isEmpty() && ing.test(disp)) return disp;
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
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        ScreenHandler handler = player.currentScreenHandler;

        // 确保在工作台界面
        if (!(handler instanceof net.minecraft.screen.CraftingScreenHandler)) return false;

        // 检查所有升级槽位是否有工作台且未禁用
        for (int i = 0; i < upgrades.getSlotCount(); i++) {
            ItemStack stack = upgrades.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.item.Items.CRAFTING_TABLE && !upgrades.isSlotDisabled(i)) {
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
		// 检查是否在白天
		if (player.getWorld().isDay()) {
			// 白天不能睡觉，发送消息
			player.sendMessage(Text.translatable("portable_storage.bed.no_sleep"), true);
			return;
		}
		
		// 检查玩家脚下是否有方块
		if (player.getWorld().getBlockState(player.getBlockPos().down()).isAir()) {
			// 不在安全位置，发送消息
			player.sendMessage(Text.translatable("portable_storage.bed.no_safe_place"), true);
			return;
		}
		
		net.minecraft.util.math.BlockPos bedPos = player.getBlockPos();
		net.minecraft.util.math.Direction facing = player.getHorizontalFacing();
		net.minecraft.util.math.BlockPos footPos = bedPos.offset(facing.getOpposite());

		// 若脚下或计划放置的脚部位置已经是床，则拒绝放置
		net.minecraft.block.BlockState curState = player.getWorld().getBlockState(bedPos);
		net.minecraft.block.BlockState footCurState = player.getWorld().getBlockState(footPos);
		if (curState.getBlock() instanceof net.minecraft.block.BedBlock || footCurState.getBlock() instanceof net.minecraft.block.BedBlock) {
			player.sendMessage(Text.translatable("portable_storage.bed.no_safe_place"), true);
			return;
		}
		
		// 检查该位置是否已经有临时床
		if (tempBeds.containsKey(bedPos) || tempBeds.containsKey(footPos)) {
			player.sendMessage(Text.translatable("portable_storage.bed.no_safe_place"), true);
			return;
		}
		
		// 检查脚部位置是否可用
		if (!player.getWorld().getBlockState(footPos).isAir()) {
			player.sendMessage(Text.translatable("portable_storage.bed.no_safe_place"), true);
			return;
		}
		
		// 存储原始方块状态和创建时间
		net.minecraft.block.BlockState headOriginalState = player.getWorld().getBlockState(bedPos);
		net.minecraft.block.BlockState footOriginalState = player.getWorld().getBlockState(footPos);
		
		// 创建完整的床方块状态（使用自定义临时床方块）
		net.minecraft.block.BlockState headBedState = com.portable.storage.block.ModBlocks.TEMP_BED.getDefaultState()
			.with(net.minecraft.block.BedBlock.FACING, facing)
			.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD)
			.with(net.minecraft.block.BedBlock.OCCUPIED, false);
			
		net.minecraft.block.BlockState footBedState = com.portable.storage.block.ModBlocks.TEMP_BED.getDefaultState()
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
				player.sendMessage(Text.translatable("portable_storage.bed_placed"), true);
			} else {
				// 如果床方块状态不正确，清理临时床
				cleanupCompleteTempBed(bedPos, player.getWorld());
				player.sendMessage(Text.translatable("portable_storage.bed.no_safe_place"), true);
			}
		} catch (Exception e) {
			// 如果睡觉失败，清理临时床并发送错误消息
			cleanupCompleteTempBed(bedPos, player.getWorld());
			player.sendMessage(Text.translatable("portable_storage.bed.no_safe_place"), true);
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
