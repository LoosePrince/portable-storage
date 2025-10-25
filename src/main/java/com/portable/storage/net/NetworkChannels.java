package com.portable.storage.net;

import com.portable.storage.net.payload.ConfigSyncS2CPayload;
import com.portable.storage.net.payload.CraftingOverlayActionC2SPayload;
import com.portable.storage.net.payload.FluidClickC2SPayload;
import com.portable.storage.net.payload.FluidConversionC2SPayload;
import com.portable.storage.net.payload.FluidSlotClickC2SPayload;
import com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload;
import com.portable.storage.net.payload.OverlayCraftingSyncS2CPayload;
import com.portable.storage.net.payload.RequestOpenScreenC2SPayload;
import com.portable.storage.net.payload.ScrollC2SPayload;
import com.portable.storage.net.payload.StorageActionC2SPayload;
import com.portable.storage.net.payload.StorageSyncS2CPayload;
import com.portable.storage.net.payload.SyncControlC2SPayload;
import com.portable.storage.net.payload.UpgradeSlotClickC2SPayload;
import com.portable.storage.net.payload.XpBottleClickC2SPayload;
import com.portable.storage.net.payload.XpBottleConversionC2SPayload;
import com.portable.storage.net.payload.XpBottleMaintenanceToggleC2SPayload;
import com.portable.storage.net.payload.SyncFilterRulesC2SPayload;
import com.portable.storage.net.payload.RequestFilterRulesSyncS2CPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class NetworkChannels {
	private NetworkChannels() {}

	public static void registerCodecs() {
		PayloadTypeRegistry.playC2S().register(SyncControlC2SPayload.ID, SyncControlC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(StorageActionC2SPayload.ID, StorageActionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ScrollC2SPayload.ID, ScrollC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CraftingOverlayActionC2SPayload.ID, CraftingOverlayActionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpgradeSlotClickC2SPayload.ID, UpgradeSlotClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FluidSlotClickC2SPayload.ID, FluidSlotClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(XpBottleClickC2SPayload.ID, XpBottleClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(XpBottleMaintenanceToggleC2SPayload.ID, XpBottleMaintenanceToggleC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(XpBottleConversionC2SPayload.ID, XpBottleConversionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FluidClickC2SPayload.ID, FluidClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FluidConversionC2SPayload.ID, FluidConversionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestOpenScreenC2SPayload.ID, RequestOpenScreenC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SyncFilterRulesC2SPayload.ID, SyncFilterRulesC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StorageSyncS2CPayload.ID, StorageSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IncrementalStorageSyncS2CPayload.ID, IncrementalStorageSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OverlayCraftingSyncS2CPayload.ID, OverlayCraftingSyncS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(ConfigSyncS2CPayload.ID, ConfigSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RequestFilterRulesSyncS2CPayload.ID, RequestFilterRulesSyncS2CPayload.CODEC);
	}
}


