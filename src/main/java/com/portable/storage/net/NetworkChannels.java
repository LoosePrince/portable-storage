package com.portable.storage.net;

import com.portable.storage.net.payload.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class NetworkChannels {
	private NetworkChannels() {}

	public static void registerCodecs() {
		PayloadTypeRegistry.playC2S().register(RequestSyncC2SPayload.ID, RequestSyncC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StorageClickC2SPayload.ID, StorageClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StorageShiftTakeC2SPayload.ID, StorageShiftTakeC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DepositSlotC2SPayload.ID, DepositSlotC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ScrollC2SPayload.ID, ScrollC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DepositCursorC2SPayload.ID, DepositCursorC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StorageSlotClickC2SPayload.ID, StorageSlotClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StorageDropC2SPayload.ID, StorageDropC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RefillCraftingC2SPayload.ID, RefillCraftingC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpgradeSlotClickC2SPayload.ID, UpgradeSlotClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EmiRecipeFillC2SPayload.ID, EmiRecipeFillC2SPayload.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestVanillaCraftingOpenC2SPayload.ID, RequestVanillaCraftingOpenC2SPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(SyncAckC2SPayload.ID, SyncAckC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StorageSyncS2CPayload.ID, StorageSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(UpgradeSyncS2CPayload.ID, UpgradeSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StorageEnablementSyncS2CPayload.ID, StorageEnablementSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IncrementalStorageSyncS2CPayload.ID, IncrementalStorageSyncS2CPayload.CODEC);
	}
}


