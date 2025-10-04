package com.portable.storage.net;

import com.portable.storage.net.payload.*;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.player.StoragePersistence;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class ServerNetworkingHandlers {
	private ServerNetworkingHandlers() {}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(RequestSyncC2SPayload.ID, (payload, context) -> {
            context.server().execute(() -> sendSync((ServerPlayerEntity) context.player()));
		});

        ServerPlayNetworking.registerGlobalReceiver(StorageClickC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.index();
			context.server().execute(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.player();
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
                sendSync(player);
			});
		});

		// 统一槽位点击，遵循原版语义
		ServerPlayNetworking.registerGlobalReceiver(StorageSlotClickC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.index();
			int button = payload.button(); // 0 左键，1 右键
            context.server().execute(() -> {
                ServerPlayerEntity player = (ServerPlayerEntity) context.player();
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
				sendSync(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DepositSlotC2SPayload.ID, (payload, context) -> {
			int handlerSlotId = payload.handlerSlotId();
			context.server().execute(() -> {
				ScreenHandler sh = context.player().currentScreenHandler;
				if (handlerSlotId < 0 || handlerSlotId >= sh.slots.size()) return;
				Slot slot = sh.getSlot(handlerSlotId);
				ItemStack from = slot.getStack();
				if (from.isEmpty()) return;

				StorageInventory inv = PlayerStorageService.getInventory(context.player());
                ItemStack remainder = insertIntoStorage(inv, from);
                slot.setStack(remainder);
				slot.markDirty();
				sh.sendContentUpdates();
				sendSync((ServerPlayerEntity) context.player());
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(DepositCursorC2SPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				var serverPlayer = (ServerPlayerEntity) context.player();
				var cursor = serverPlayer.currentScreenHandler.getCursorStack();
				if (cursor.isEmpty()) return;
				StorageInventory inv = PlayerStorageService.getInventory(serverPlayer);
                ItemStack remainder = insertIntoStorage(inv, cursor);
				serverPlayer.currentScreenHandler.setCursorStack(remainder);
				serverPlayer.currentScreenHandler.sendContentUpdates();
				sendSync(serverPlayer);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ScrollC2SPayload.ID, (payload, context) -> {
			// 当前不持久化滚动，仅留作扩展
		});

		ServerPlayNetworking.registerGlobalReceiver(RefillCraftingC2SPayload.ID, (payload, context) -> {
			int slotIndex = payload.slotIndex();
			ItemStack targetStack = payload.targetStack();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();

				// 检查是否有工作台升级且未禁用
				if (!portableStorage$hasCraftingTableUpgrade(player)) return;

				ScreenHandler handler = player.currentScreenHandler;

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
				
				// 计算需要补充的数量
				int currentCount = current.isEmpty() ? 0 : current.getCount();
				int needed = targetStack.getCount() - currentCount;
				
				// 如果不需要补充或已经足够，直接返回
				if (needed <= 0) return;
				
                // 从合并视图取出：优先自己，其次共享对象
                int totalTaken = 0;
                java.util.List<StorageInventory> view = getViewStorages(player);
                for (StorageInventory s : view) {
                    for (int i = 0; i < s.getCapacity() && totalTaken < needed; i++) {
                        ItemStack storageStack = s.getDisplayStack(i);
                        if (storageStack.isEmpty()) continue;
                        if (ItemStack.areItemsAndComponentsEqual(targetStack, storageStack)) {
                            long available = s.getCountByIndex(i);
                            if (available > 0) {
                                int canTake = Math.min(needed - totalTaken, (int)Math.min(available, targetStack.getMaxCount()));
                                if (canTake > 0) {
                                    long taken = s.takeByIndex(i, canTake, System.currentTimeMillis());
                                    totalTaken += (int)taken;
                                }
                            }
                        }
                    }
                    if (totalTaken >= needed) break;
                }
				
				// 如果成功取出物品，放入合成槽位
				if (totalTaken > 0) {
					if (current.isEmpty()) {
						ItemStack newStack = targetStack.copy();
						newStack.setCount(totalTaken);
						slot.setStack(newStack);
					} else {
						current.increment(totalTaken);
					}
					
					slot.markDirty();
					handler.sendContentUpdates();
					sendSync(player);
				}
			});
		});

		// 升级槽位点击
		ServerPlayNetworking.registerGlobalReceiver(UpgradeSlotClickC2SPayload.ID, (payload, context) -> {
			int slot = payload.slot();
			int button = payload.button();
			context.server().execute(() -> {
				ServerPlayerEntity player = (ServerPlayerEntity) context.player();
				UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);

				if (slot < 0 || slot >= upgrades.getSlotCount()) return;

				// 右键点击切换禁用状态
				if (button == 1) {
					upgrades.toggleSlotDisabled(slot);
					sendSync(player);
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
						player.currentScreenHandler.setCursorStack(taken);
						player.currentScreenHandler.sendContentUpdates();
						sendSync(player);
					}
				} else {
					// 手持物品
					if (slotStack.isEmpty()) {
						// 槽位为空，尝试放入
						if (cursor.getCount() == 1) {
							if (upgrades.tryInsert(slot, cursor)) {
								player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
								player.currentScreenHandler.sendContentUpdates();
								sendSync(player);
							}
						}
					} else {
						// 槽位有物品，交换
						if (cursor.getCount() == 1) {
							ItemStack taken = upgrades.takeStack(slot);
							if (upgrades.tryInsert(slot, cursor)) {
								player.currentScreenHandler.setCursorStack(taken);
								player.currentScreenHandler.sendContentUpdates();
								sendSync(player);
							} else {
								// 放入失败，恢复
								upgrades.setStack(slot, taken);
							}
						}
					}
				}
			});
		});

	}

	private static net.minecraft.nbt.NbtCompound getOrCreateCustom(net.minecraft.item.ItemStack stack) {
		net.minecraft.component.type.NbtComponent comp = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
		return comp != null ? comp.copyNbt() : new net.minecraft.nbt.NbtCompound();
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
        // Preserve full components by inserting the whole ItemStack variant
        storage.insertItemStack(stack.copy(), System.currentTimeMillis());
        return ItemStack.EMPTY;
    }

    private static long insertIntoStorageAsLong(StorageInventory storage, net.minecraft.item.Item item, long count) {
        if (count <= 0) return 0;
        // Fallback path kept for existing callers; wrap into ItemStack to preserve components if possible
        ItemStack tmp = new ItemStack(item);
        tmp.setCount((int)Math.min(Integer.MAX_VALUE, count));
        storage.insertItemStack(tmp, System.currentTimeMillis());
        return 0;
    }

    public static void sendSync(ServerPlayerEntity player) {
        StorageInventory merged = buildMergedSnapshot(player);
        NbtCompound nbt = new NbtCompound();
        merged.writeNbt(nbt);
        ServerPlayNetworking.send(player, new StorageSyncS2CPayload(nbt));
		sendUpgradeSync(player);
	}

	public static void sendUpgradeSync(ServerPlayerEntity player) {
		UpgradeInventory up = PlayerStorageService.getUpgradeInventory(player);
		NbtCompound nbt = new NbtCompound();
		up.writeNbt(nbt);
		ServerPlayNetworking.send(player, new UpgradeSyncS2CPayload(nbt));
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

    private static StorageInventory buildMergedSnapshot(ServerPlayerEntity viewer) {
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
            sendSync(p);
        }
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
}




