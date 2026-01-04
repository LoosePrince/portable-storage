package com.portablestorage.network;

import com.portablestorage.config.ModConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ModClientNetworking {
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ModConfig.setActive3x3Crafting(payload.enable3x3Crafting());
                ModConfig.dropStorageOnDeath = payload.dropStorageOnDeath();
                ModConfig.allowHotReload = payload.allowHotReload();
                ModConfig.maxStorageTypes = payload.maxStorageTypes();
                ModConfig.maxItemStackSize = payload.maxItemStackSize();
                ModConfig.baseMaxStorageTypes = payload.baseMaxStorageTypes();
                ModConfig.baseMaxItemStackSize = payload.baseMaxItemStackSize();
                ModConfig.unconditionalWarehouse = payload.unconditionalWarehouse();
                ModConfig.hopperRange = payload.hopperRange();
                ModConfig.hopperFrequency = payload.hopperFrequency();
                ModConfig.riftUpgradeItem = payload.riftUpgradeItem();
                ModConfig.riftChunkSize = payload.riftChunkSize();
                ModConfig.enableRiftForcedLoading = payload.enableRiftForcedLoading();
                ModConfig.riftForcedLoadingRange = payload.riftForcedLoadingRange();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(S2COpenHopperFilterPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(com.portablestorage.config.YACLConfig.createHopperFilterScreen(context.client().screen, payload.filters(), payload.blacklist()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(S2COpenFoodFilterPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(com.portablestorage.config.YACLConfig.createFoodFilterScreen(context.client().screen, payload.filters(), payload.blacklist()));
            });
        });
    }
}

