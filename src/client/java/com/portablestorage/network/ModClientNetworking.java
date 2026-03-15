package com.portablestorage.network;

import com.portablestorage.config.ModConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端网络处理器
 * 处理服务端发送到客户端的网络数据包
 */
public class ModClientNetworking {
    // 客户端缓存：当前玩家是否被服务端允许编辑服务器配置
    private static volatile boolean canEditServerConfig = false;

    public static boolean canEditServerConfig() {
        return canEditServerConfig;
    }

    public static void requestConfigPermission() {
        ClientPlayNetworking.send(new C2SQueryConfigPermissionPayload());
    }

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
                ModConfig.maxItemNbtSize = payload.maxItemNbtSize();
                ModConfig.unconditionalWarehouse = payload.unconditionalWarehouse();
                ModConfig.baseWarehouseActivationItem = payload.baseWarehouseActivationItem();
                ModConfig.fullWarehouseActivationItem = payload.fullWarehouseActivationItem();
                ModConfig.hopperRange = payload.hopperRange();
                ModConfig.hopperFrequency = payload.hopperFrequency();
                ModConfig.lavaInfiniteThreshold = payload.lavaInfiniteThreshold();
                ModConfig.waterInfiniteThreshold = payload.waterInfiniteThreshold();
                ModConfig.riftUpgradeItem = payload.riftUpgradeItem();
                ModConfig.riftChunkSize = payload.riftChunkSize();
                ModConfig.enableRiftForcedLoading = payload.enableRiftForcedLoading();
                ModConfig.riftForcedLoadingRange = payload.riftForcedLoadingRange();
                ModConfig.enableConduitUpgrade = payload.enableConduitUpgrade();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(S2CConfigPermissionResultPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                canEditServerConfig = payload.canEdit();
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

