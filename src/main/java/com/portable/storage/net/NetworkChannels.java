package com.portable.storage.net;

import com.portable.storage.net.payload.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class NetworkChannels {
	private NetworkChannels() {}

	public static void registerCodecs() {
		PayloadTypeRegistry.playC2S().register(RequestSyncC2SPayload.ID, RequestSyncC2SPayload.CODEC);
		// 新统一动作包
		PayloadTypeRegistry.playC2S().register(StorageActionC2SPayload.ID, StorageActionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ScrollC2SPayload.ID, ScrollC2SPayload.CODEC);
		// 移除旧点击/投递/丢弃类的注册
		PayloadTypeRegistry.playC2S().register(CraftingOverlayActionC2SPayload.ID, CraftingOverlayActionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpgradeSlotClickC2SPayload.ID, UpgradeSlotClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FluidSlotClickC2SPayload.ID, FluidSlotClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(XpBottleClickC2SPayload.ID, XpBottleClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(XpBottleMaintenanceToggleC2SPayload.ID, XpBottleMaintenanceToggleC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(XpBottleConversionC2SPayload.ID, XpBottleConversionC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FluidClickC2SPayload.ID, FluidClickC2SPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FluidConversionC2SPayload.ID, FluidConversionC2SPayload.CODEC);
		// 旧覆盖合成/配方填充类注册移除
        PayloadTypeRegistry.playC2S().register(RequestVanillaCraftingOpenC2SPayload.ID, RequestVanillaCraftingOpenC2SPayload.PACKET_CODEC);
		PayloadTypeRegistry.playC2S().register(com.portable.storage.net.payload.RequestPortableCraftingOpenC2SPayload.ID, com.portable.storage.net.payload.RequestPortableCraftingOpenC2SPayload.PACKET_CODEC);
		// 移除 OverlayCrafting* 旧注册
		PayloadTypeRegistry.playC2S().register(SyncAckC2SPayload.ID, SyncAckC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StorageSyncS2CPayload.ID, StorageSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.portable.storage.net.payload.OverlayCraftingSyncS2CPayload.ID, com.portable.storage.net.payload.OverlayCraftingSyncS2CPayload.PACKET_CODEC);
		PayloadTypeRegistry.playS2C().register(UpgradeSyncS2CPayload.ID, UpgradeSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StorageEnablementSyncS2CPayload.ID, StorageEnablementSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IncrementalStorageSyncS2CPayload.ID, IncrementalStorageSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(XpStepSyncS2CPayload.ID, XpStepSyncS2CPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ContainerDisplayConfigSyncS2CPayload.ID, ContainerDisplayConfigSyncS2CPayload.CODEC);
	}
}


