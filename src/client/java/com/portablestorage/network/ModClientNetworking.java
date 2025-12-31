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
            });
        });
    }
}

