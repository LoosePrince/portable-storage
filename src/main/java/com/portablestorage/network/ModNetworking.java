package com.portablestorage.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ModNetworking {
    public static void registerC2SPayloads() {
        PayloadTypeRegistry.playC2S().register(ScrollPayload.TYPE, ScrollPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SearchPayload.TYPE, SearchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChangeRowsPayload.TYPE, ChangeRowsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuickTransferPayload.ID, QuickTransferPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenCraftingPayload.TYPE, OpenCraftingPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RefillPayload.TYPE, RefillPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateServerConfigPayload.TYPE, UpdateServerConfigPayload.CODEC);
    }

    public static void registerS2CPayloads() {
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(UpdateSettingsPayload.TYPE, ModServerNetworking::handleUpdateSettings);
        ServerPlayNetworking.registerGlobalReceiver(ChangeRowsPayload.TYPE, ModServerNetworking::handleChangeRows);
        ServerPlayNetworking.registerGlobalReceiver(ScrollPayload.TYPE, ModServerNetworking::handleScroll);
        ServerPlayNetworking.registerGlobalReceiver(SearchPayload.TYPE, ModServerNetworking::handleSearch);
        ServerPlayNetworking.registerGlobalReceiver(QuickTransferPayload.ID, ModServerNetworking::handleQuickTransfer);
        ServerPlayNetworking.registerGlobalReceiver(OpenCraftingPayload.TYPE, ModServerNetworking::handleOpenCrafting);
        ServerPlayNetworking.registerGlobalReceiver(RefillPayload.TYPE, ModServerNetworking::handleRefill);
        ServerPlayNetworking.registerGlobalReceiver(UpdateServerConfigPayload.TYPE, ModServerNetworking::handleUpdateServerConfig);
    }
}

