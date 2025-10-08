package com.portable.storage.client;

import com.portable.storage.net.payload.StorageEnablementSyncS2CPayload;
import com.portable.storage.net.payload.StorageSyncS2CPayload;
import com.portable.storage.net.payload.UpgradeSyncS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworkingHandlers {
	private ClientNetworkingHandlers() {}

	public static void register() {
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
	}
}


