package com.portable.storage.client;

import com.portable.storage.net.payload.StorageEnablementSyncS2CPayload;
import com.portable.storage.net.payload.StorageSyncS2CPayload;
import com.portable.storage.net.payload.UpgradeSyncS2CPayload;
import com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload;
import com.portable.storage.net.payload.XpBottleMaintenanceToggleC2SPayload;
import com.portable.storage.net.payload.XpStepSyncS2CPayload;
import com.portable.storage.net.payload.ContainerDisplayConfigSyncS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworkingHandlers {
	private ClientNetworkingHandlers() {}

	public static void register() {
		// 注册增量同步处理器（优先）
		ClientPlayNetworking.registerGlobalReceiver(IncrementalStorageSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().player == null) return;
				IncrementalStorageState.handleIncrementalSync(payload, context.client().player.getRegistryManager());
			});
		});
		
		// 保留原有的全量同步处理器作为后备
		ClientPlayNetworking.registerGlobalReceiver(StorageSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().player == null) return;
				ClientStorageState.updateFromNbt(payload.nbt(), context.client().player.getRegistryManager());
			});
		});
		
		ClientPlayNetworking.registerGlobalReceiver(UpgradeSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().player == null) return;
				ClientUpgradeState.updateFromNbt(payload.data());
			});
		});
		
		ClientPlayNetworking.registerGlobalReceiver(StorageEnablementSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().player == null) return;
				ClientStorageState.setStorageEnabled(payload.enabled());
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
		
		ClientPlayNetworking.registerGlobalReceiver(XpStepSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().player == null) return;
				ClientUpgradeState.setXpTransferStep(payload.stepIndex());
			});
		});
		
		// 容器显示配置同步
		ClientPlayNetworking.registerGlobalReceiver(ContainerDisplayConfigSyncS2CPayload.ID, (payload, context) -> {
			context.client().execute(() -> {
				ClientContainerDisplayConfig.getInstance().updateConfig(
					payload.stonecutter(),
					payload.cartographyTable(),
					payload.smithingTable(),
					payload.grindstone(),
					payload.loom(),
					payload.furnace(),
					payload.smoker(),
					payload.blastFurnace(),
					payload.anvil(),
					payload.enchantingTable(),
					payload.brewingStand(),
					payload.beacon(),
					payload.chest(),
					payload.barrel(),
					payload.enderChest(),
					payload.shulkerBox(),
					payload.dispenser(),
					payload.dropper(),
					payload.crafter(),
					payload.hopper(),
					payload.trappedChest(),
					payload.hopperMinecart(),
					payload.chestMinecart(),
					payload.chestBoat(),
					payload.bambooChestRaft()
				);
			});
		});
	}
	
	public static void sendXpBottleMaintenanceToggle() {
		ClientPlayNetworking.send(new XpBottleMaintenanceToggleC2SPayload());
	}
	
	public static void sendRefundCraftingSlots() {
		ClientPlayNetworking.send(new com.portable.storage.net.payload.RefundCraftingSlotsC2SPayload());
	}
}


