package com.portablestorage.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ModNetworking {
    public static void registerC2SPayloads() {
        PayloadTypeRegistry.playC2S().register(C2SUpdateWarehouseStatePayload.TYPE, C2SUpdateWarehouseStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuickTransferPayload.TYPE, QuickTransferPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenCraftingPayload.TYPE, OpenCraftingPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RefillPayload.TYPE, RefillPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateServerConfigPayload.TYPE, UpdateServerConfigPayload.CODEC);
    }

    public static void registerS2CPayloads() {
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(C2SUpdateWarehouseStatePayload.TYPE, ModServerNetworking::handleUpdateWarehouseState);
        ServerPlayNetworking.registerGlobalReceiver(QuickTransferPayload.TYPE, ModServerNetworking::handleQuickTransfer);
        ServerPlayNetworking.registerGlobalReceiver(OpenCraftingPayload.TYPE, ModServerNetworking::handleOpenCrafting);
        ServerPlayNetworking.registerGlobalReceiver(RefillPayload.TYPE, ModServerNetworking::handleRefill);
        ServerPlayNetworking.registerGlobalReceiver(UpdateServerConfigPayload.TYPE, ModServerNetworking::handleUpdateServerConfig);
    }
}

