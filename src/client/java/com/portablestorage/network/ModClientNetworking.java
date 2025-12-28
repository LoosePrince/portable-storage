package com.portablestorage.network;

import com.portablestorage.config.ModConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ModClientNetworking {
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ModConfig.setActive3x3Crafting(payload.enable3x3Crafting());
                ModConfig.dropStorageOnDeath = payload.dropStorageOnDeath();
            });
        });
    }
}

