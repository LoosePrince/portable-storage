package com.portable.storage.client;

import com.portable.storage.net.payload.StorageSyncS2CPayload;
import com.portable.storage.net.payload.XpBottleMaintenanceToggleC2SPayload;
import com.portable.storage.net.payload.ConfigSyncS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworkingHandlers {
	private ClientNetworkingHandlers() {}

	public static void register() {
		// 增量同步通道已移除
		// 注册新的增量同步接收器
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
			com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload.ID,
			(payload, context) -> context.client().execute(() -> {
				if (context.client().player == null) return;
				com.portable.storage.client.ClientStorageState.applyDiff(payload.sessionId(), payload.seq(), payload.diff());
				// 增量应用后立即ACK当前序号（无需阻塞UI）
				try {
					net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.portable.storage.net.payload.SyncControlC2SPayload(
							com.portable.storage.net.payload.SyncControlC2SPayload.Op.ACK,
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
                            com.portable.storage.client.ClientRiftConfig.updateConfig(upgradeItem, size);
                        }
                    }
                    case VIRTUAL_CRAFTING_CONFIG -> {
                        var nbt = payload.data();
                        if (nbt != null) {
                            boolean enabled = nbt.getBoolean("enableVirtualCrafting");
                            com.portable.storage.client.ClientVirtualCraftingConfig.updateConfig(enabled);
                            
                            // 如果服务端禁用虚拟合成，强制关闭客户端的虚拟合成显示
                            if (!enabled) {
                                com.portable.storage.client.ClientConfig config = com.portable.storage.client.ClientConfig.getInstance();
                                if (config.virtualCraftingVisible) {
                                    config.virtualCraftingVisible = false;
                                    com.portable.storage.client.ClientConfig.save();
                                    // 返还所有合成槽位的物品
                                    com.portable.storage.client.ClientNetworkingHandlers.sendRefundCraftingSlots();
                                }
                            }
                        }
                    }
                }
            });
        });

		// 覆盖层虚拟合成同步
		ClientPlayNetworking.registerGlobalReceiver(com.portable.storage.net.payload.OverlayCraftingSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				try {
					// 将最新的虚拟槽位状态交给 UI 组件缓存（如需渲染数量/图标）
					com.portable.storage.client.ui.VirtualCraftingOverlayState.update(payload.slots());
				} catch (Throwable ignored) {}
			});
		});
		
        // 旧 XP_STEP / DISPLAY_CONFIG / UPGRADE / ENABLEMENT 的接收器已由 ConfigSyncS2CPayload 统一替代
	}
	
	public static void sendXpBottleMaintenanceToggle() {
		ClientPlayNetworking.send(new XpBottleMaintenanceToggleC2SPayload());
	}
	
	public static void sendRefundCraftingSlots() {
		ClientPlayNetworking.send(new com.portable.storage.net.payload.CraftingOverlayActionC2SPayload(
			com.portable.storage.net.payload.CraftingOverlayActionC2SPayload.Action.REFUND,
			0, 0, false,
			net.minecraft.item.ItemStack.EMPTY,
			"",
			null,
			null
		));
	}
}


