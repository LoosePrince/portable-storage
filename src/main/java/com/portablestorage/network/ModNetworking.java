package com.portablestorage.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 网络注册类
 * 负责注册客户端到服务端和服务端到客户端的网络数据包类型和接收器
 */
public class ModNetworking {
        public static void registerC2SPayloads() {
                PayloadTypeRegistry.playC2S().register(C2SUpdateWarehouseStatePayload.TYPE,
                                C2SUpdateWarehouseStatePayload.CODEC);
                PayloadTypeRegistry.playC2S().register(QuickTransferPayload.TYPE, QuickTransferPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(OpenCraftingPayload.TYPE, OpenCraftingPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(RefillPayload.TYPE, RefillPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(UpdateServerConfigPayload.TYPE, UpdateServerConfigPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SUpgradeInteractionPayload.TYPE,
                                C2SUpgradeInteractionPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SUpdateHopperFiltersPayload.TYPE,
                                C2SUpdateHopperFiltersPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SUpdateFoodFiltersPayload.TYPE,
                                C2SUpdateFoodFiltersPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2STogglePinnedPayload.TYPE, C2STogglePinnedPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SRecipeTransferPayload.TYPE, C2SRecipeTransferPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SUpdateForbiddenPlayersPayload.TYPE,
                                C2SUpdateForbiddenPlayersPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SDropWarehouseItemPayload.TYPE,
                                C2SDropWarehouseItemPayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SUpdateFrozenStatePayload.TYPE,
                                C2SUpdateFrozenStatePayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SDoubleClickQuickStorePayload.TYPE,
                                C2SDoubleClickQuickStorePayload.CODEC);
                PayloadTypeRegistry.playC2S().register(C2SQueryConfigPermissionPayload.TYPE,
                                C2SQueryConfigPermissionPayload.CODEC);
        }

        public static void registerS2CPayloads() {
                PayloadTypeRegistry.playS2C().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
                PayloadTypeRegistry.playS2C().register(S2CConfigPermissionResultPayload.TYPE,
                                S2CConfigPermissionResultPayload.CODEC);
                PayloadTypeRegistry.playS2C().register(S2COpenHopperFilterPayload.TYPE,
                                S2COpenHopperFilterPayload.CODEC);
                PayloadTypeRegistry.playS2C().register(S2COpenFoodFilterPayload.TYPE, S2COpenFoodFilterPayload.CODEC);
                PayloadTypeRegistry.playS2C().register(S2CWarehousePinnedUpdatePayload.TYPE,
                                S2CWarehousePinnedUpdatePayload.CODEC);
        }

        public static void registerServerReceivers() {
                ServerPlayNetworking.registerGlobalReceiver(C2SUpdateWarehouseStatePayload.TYPE,
                                ModServerNetworking::handleUpdateWarehouseState);
                ServerPlayNetworking.registerGlobalReceiver(QuickTransferPayload.TYPE,
                                ModServerNetworking::handleQuickTransfer);
                ServerPlayNetworking.registerGlobalReceiver(OpenCraftingPayload.TYPE,
                                ModServerNetworking::handleOpenCrafting);
                ServerPlayNetworking.registerGlobalReceiver(RefillPayload.TYPE, ModServerNetworking::handleRefill);
                ServerPlayNetworking.registerGlobalReceiver(UpdateServerConfigPayload.TYPE,
                                ModServerNetworking::handleUpdateServerConfig);
                ServerPlayNetworking.registerGlobalReceiver(C2SUpgradeInteractionPayload.TYPE,
                                ModServerNetworking::handleUpgradeInteraction);
                ServerPlayNetworking.registerGlobalReceiver(C2SUpdateHopperFiltersPayload.TYPE,
                                ModServerNetworking::handleUpdateHopperFilters);
                ServerPlayNetworking.registerGlobalReceiver(C2SUpdateFoodFiltersPayload.TYPE,
                                ModServerNetworking::handleUpdateFoodFilters);
                ServerPlayNetworking.registerGlobalReceiver(C2STogglePinnedPayload.TYPE,
                                ModServerNetworking::handleTogglePinned);
                ServerPlayNetworking.registerGlobalReceiver(C2SRecipeTransferPayload.TYPE,
                                ModServerNetworking::handleRecipeTransfer);
                ServerPlayNetworking.registerGlobalReceiver(C2SUpdateForbiddenPlayersPayload.TYPE,
                                ModServerNetworking::handleUpdateForbiddenPlayers);
                ServerPlayNetworking.registerGlobalReceiver(C2SDropWarehouseItemPayload.TYPE,
                                ModServerNetworking::handleDropWarehouseItem);
                ServerPlayNetworking.registerGlobalReceiver(C2SUpdateFrozenStatePayload.TYPE,
                                ModServerNetworking::handleUpdateFrozenState);
                ServerPlayNetworking.registerGlobalReceiver(C2SDoubleClickQuickStorePayload.TYPE,
                                ModServerNetworking::handleDoubleClickQuickStore);
                ServerPlayNetworking.registerGlobalReceiver(C2SQueryConfigPermissionPayload.TYPE,
                                ModServerNetworking::handleQueryConfigPermission);
        }
}
