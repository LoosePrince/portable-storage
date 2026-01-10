package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import com.portablestorage.config.ModConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端网络处理器
 * 处理服务端发送到客户端的网络数据包
 */
public class ModClientNetworking {
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(PortableStorage.id("sync_config"), (client, handler, buf, responseSender) -> {
            SyncConfigPayload payload = new SyncConfigPayload(buf);
            client.execute(() -> {
                ModConfig.setActive3x3Crafting(payload.enable3x3Crafting());
                ModConfig.dropStorageOnDeath = payload.dropStorageOnDeath();
                ModConfig.allowHotReload = payload.allowHotReload();
                ModConfig.maxStorageTypes = payload.maxStorageTypes();
                ModConfig.maxItemStackSize = payload.maxItemStackSize();
                ModConfig.baseMaxStorageTypes = payload.baseMaxStorageTypes();
                ModConfig.baseMaxItemStackSize = payload.baseMaxItemStackSize();
                ModConfig.maxItemNbtSize = payload.maxItemNbtSize();
                ModConfig.unconditionalWarehouse = payload.unconditionalWarehouse();
                ModConfig.hopperRange = payload.hopperRange();
                ModConfig.hopperFrequency = payload.hopperFrequency();
                ModConfig.lavaInfiniteThreshold = payload.lavaInfiniteThreshold();
                ModConfig.waterInfiniteThreshold = payload.waterInfiniteThreshold();
                ModConfig.riftUpgradeItem = payload.riftUpgradeItem();
                ModConfig.riftChunkSize = payload.riftChunkSize();
                ModConfig.enableRiftForcedLoading = payload.enableRiftForcedLoading();
                ModConfig.riftForcedLoadingRange = payload.riftForcedLoadingRange();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PortableStorage.id("open_hopper_filter"), (client, handler, buf, responseSender) -> {
            S2COpenHopperFilterPayload payload = new S2COpenHopperFilterPayload(buf);
            client.execute(() -> {
                client.setScreen(com.portablestorage.config.YACLConfig.createHopperFilterScreen(client.screen, payload.filters(), payload.blacklist()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PortableStorage.id("open_food_filter"), (client, handler, buf, responseSender) -> {
            S2COpenFoodFilterPayload payload = new S2COpenFoodFilterPayload(buf);
            client.execute(() -> {
                client.setScreen(com.portablestorage.config.YACLConfig.createFoodFilterScreen(client.screen, payload.filters(), payload.blacklist()));
            });
        });
    }
}

