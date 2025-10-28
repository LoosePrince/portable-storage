package com.portable.storage.client;

import com.portable.storage.client.ui.VirtualCraftingOverlayState;
import com.portable.storage.net.payload.ConfigSyncS2CPayload;
import com.portable.storage.net.payload.CraftingOverlayActionC2SPayload;
import com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload;
import com.portable.storage.net.payload.OverlayCraftingSyncS2CPayload;
import com.portable.storage.net.payload.StorageSyncS2CPayload;
import com.portable.storage.net.payload.SyncControlC2SPayload;
import com.portable.storage.net.payload.XpBottleMaintenanceToggleC2SPayload;
import com.portable.storage.net.payload.RequestFilterRulesSyncS2CPayload;
import com.portable.storage.net.payload.OpenBarrelFilterS2CPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworkingHandlers {
	private ClientNetworkingHandlers() {}

	public static void register() {
		// 增量同步通道已移除
		// 注册新的增量同步接收器
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
			IncrementalStorageSyncS2CPayload.ID,
			(payload, context) -> context.client().execute(() -> {
				if (context.client().player == null) return;
				ClientStorageState.applyDiff(payload.sessionId(), payload.seq(), payload.diff());
				// 增量应用后立即ACK当前序号（无需阻塞UI）
				try {
					net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new SyncControlC2SPayload(
							SyncControlC2SPayload.Op.ACK,
							payload.seq(),
							true
						)
					);
				} catch (Throwable ignored) {}
			})
		);

		// 保留原有的全量同步处理器作为后备
		ClientPlayNetworking.registerGlobalReceiver(StorageSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().player == null) return;
				ClientStorageState.updateFromNbt(payload.nbt(), context.client().player.getRegistryManager());
			});
		});
		
        // 统一配置/状态同步
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) return;
                switch (payload.topic()) {
                    case UPGRADE -> {
                        var nbt = payload.data();
                        if (nbt != null) ClientUpgradeState.updateFromNbt(nbt);
                    }
                    case STORAGE_ENABLEMENT -> {
                        var nbt = payload.data();
                        if (nbt != null && nbt.contains("enabled")) {
                            ClientStorageState.setStorageEnabled(nbt.getBoolean("enabled"));
                        }
                    }
                    case XP_STEP -> {
                        var nbt = payload.data();
                        if (nbt != null && nbt.contains("stepIndex")) {
                            ClientUpgradeState.setXpTransferStep(nbt.getInt("stepIndex"));
                        }
                    }
                    case AUTO_EAT_MODE -> {
                        var nbt = payload.data();
                        if (nbt != null && nbt.contains("modeIndex")) {
                            int modeIndex = nbt.getInt("modeIndex");
                            com.portable.storage.storage.AutoEatMode mode = com.portable.storage.storage.AutoEatMode.fromIndex(modeIndex);
                            ClientUpgradeState.setAutoEatMode(mode);
                        }
                    }
                    case DISPLAY_CONFIG -> {
                        var nbt = payload.data();
                        if (nbt != null) {
                            ClientContainerDisplayConfig.getInstance().updateConfig(
                                nbt.getBoolean("stonecutter"),
                                nbt.getBoolean("cartographyTable"),
                                nbt.getBoolean("smithingTable"),
                                nbt.getBoolean("grindstone"),
                                nbt.getBoolean("loom"),
                                nbt.getBoolean("furnace"),
                                nbt.getBoolean("smoker"),
                                nbt.getBoolean("blastFurnace"),
                                nbt.getBoolean("anvil"),
                                nbt.getBoolean("enchantingTable"),
                                nbt.getBoolean("brewingStand"),
                                nbt.getBoolean("beacon"),
                                nbt.getBoolean("chest"),
                                nbt.getBoolean("barrel"),
                                nbt.getBoolean("enderChest"),
                                nbt.getBoolean("shulkerBox"),
                                nbt.getBoolean("dispenser"),
                                nbt.getBoolean("dropper"),
                                nbt.getBoolean("crafter"),
                                nbt.getBoolean("hopper"),
                                nbt.getBoolean("trappedChest"),
                                nbt.getBoolean("hopperMinecart"),
                                nbt.getBoolean("chestMinecart"),
                                nbt.getBoolean("chestBoat"),
                                nbt.getBoolean("bambooChestRaft")
                            );
                        }
                    }
                    case RIFT_CONFIG -> {
                        var nbt = payload.data();
                        if (nbt != null) {
                            String upgradeItem = nbt.getString("riftUpgradeItem");
                            int size = nbt.getInt("riftSize");
                            ClientRiftConfig.updateConfig(upgradeItem, size);
                        }
                    }
                    case VIRTUAL_CRAFTING_CONFIG -> {
                        var nbt = payload.data();
                        if (nbt != null) {
                            boolean enabled = nbt.getBoolean("enableVirtualCrafting");
                            ClientVirtualCraftingConfig.updateConfig(enabled);
                            
                            // 如果服务端禁用虚拟合成，强制关闭客户端的虚拟合成显示
                            if (!enabled) {
                                ClientConfig config = ClientConfig.getInstance();
                                if (config.virtualCraftingVisible) {
                                    config.virtualCraftingVisible = false;
                                    ClientConfig.save();
                                    // 返还所有合成槽位的物品
                                    ClientNetworkingHandlers.sendRefundCraftingSlots();
                                }
                            }
                        }
                    }
                    case INFINITE_FLUID_CONFIG -> {
                        var nbt = payload.data();
                        if (nbt != null) {
                            boolean enableLava = nbt.getBoolean("enableInfiniteLava");
                            boolean enableWater = nbt.getBoolean("enableInfiniteWater");
                            int lavaThreshold = nbt.getInt("infiniteLavaThreshold");
                            int waterThreshold = nbt.getInt("infiniteWaterThreshold");
                            ClientInfiniteFluidConfig.updateConfig(enableLava, enableWater, lavaThreshold, waterThreshold);
                        }
                    }
                }
            });
        });

		// 覆盖层虚拟合成同步
		ClientPlayNetworking.registerGlobalReceiver(OverlayCraftingSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				try {
					// 将最新的虚拟槽位状态交给 UI 组件缓存（如需渲染数量/图标）
					VirtualCraftingOverlayState.update(payload.slots());
				} catch (Throwable ignored) {}
			});
		});
		
        // 旧 XP_STEP / DISPLAY_CONFIG / UPGRADE / ENABLEMENT 的接收器已由 ConfigSyncS2CPayload 统一替代
        
        // 筛选界面不需要服务器端处理，直接由客户端管理
        // 但绑定木桶筛选需要特殊处理
        ClientPlayNetworking.registerGlobalReceiver(OpenBarrelFilterS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // 直接打开绑定木桶筛选界面
                context.client().setScreen(new com.portable.storage.client.screen.FilterListScreen(
                    context.client().currentScreen, 
                    com.portable.storage.client.screen.FilterListScreen.Mode.FILTER, 
                    payload.barrelPos()
                ));
            });
        });
        
        // 处理服务器请求同步筛选规则
        ClientPlayNetworking.registerGlobalReceiver(RequestFilterRulesSyncS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player == null) return;
                // 主动同步筛选规则到服务器
                syncFilterRulesToServer();
            });
        });
	}
	
	public static void sendXpBottleMaintenanceToggle() {
		ClientPlayNetworking.send(new XpBottleMaintenanceToggleC2SPayload());
	}
	
	public static void sendRefundCraftingSlots() {
		ClientPlayNetworking.send(new CraftingOverlayActionC2SPayload(
			CraftingOverlayActionC2SPayload.Action.REFUND,
			0, 0, false,
			net.minecraft.item.ItemStack.EMPTY,
			"",
			null,
			null
		));
	}
	
	/**
	 * 同步筛选规则到服务器
	 */
	private static void syncFilterRulesToServer() {
		// 转换规则格式
		java.util.List<com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule> serverFilterRules = new java.util.ArrayList<>();
		java.util.List<com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule> serverDestroyRules = new java.util.ArrayList<>();
		
		for (com.portable.storage.client.ClientConfig.FilterRule rule : com.portable.storage.client.ClientConfig.getInstance().filterRules) {
			serverFilterRules.add(new com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule(
				rule.matchRule, rule.isWhitelist, rule.enabled
			));
		}
		
		for (com.portable.storage.client.ClientConfig.FilterRule rule : com.portable.storage.client.ClientConfig.getInstance().destroyRules) {
			serverDestroyRules.add(new com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule(
				rule.matchRule, rule.isWhitelist, rule.enabled
			));
		}
		
		// 发送到服务器
		ClientPlayNetworking.send(
			new com.portable.storage.net.payload.SyncFilterRulesC2SPayload(serverFilterRules, serverDestroyRules)
		);
	}
}


