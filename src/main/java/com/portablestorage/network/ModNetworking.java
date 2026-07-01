package com.portablestorage.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 网络注册类
 * 负责注册客户端到服务端和服务端到客户端的网络数据包类型和接收器
 */
public class ModNetworking {
        public static void registerC2SPayloads() {
                PayloadTypeRegistry.serverboundPlay().register(C2SUpdateWarehouseStatePayload.TYPE,
                                C2SUpdateWarehouseStatePayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(QuickTransferPayload.TYPE, QuickTransferPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(OpenCraftingPayload.TYPE, OpenCraftingPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(RefillPayload.TYPE, RefillPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(UpdateServerConfigPayload.TYPE, UpdateServerConfigPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SUpgradeInteractionPayload.TYPE,
                                C2SUpgradeInteractionPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SUpdateHopperFiltersPayload.TYPE,
                                C2SUpdateHopperFiltersPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SUpdateFoodFiltersPayload.TYPE,
                                C2SUpdateFoodFiltersPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2STogglePinnedPayload.TYPE, C2STogglePinnedPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SRecipeTransferPayload.TYPE, C2SRecipeTransferPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SUpdateForbiddenPlayersPayload.TYPE,
                                C2SUpdateForbiddenPlayersPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SDropWarehouseItemPayload.TYPE,
                                C2SDropWarehouseItemPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SUpdateFrozenStatePayload.TYPE,
                                C2SUpdateFrozenStatePayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SDoubleClickQuickStorePayload.TYPE,
                                C2SDoubleClickQuickStorePayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SQueryConfigPermissionPayload.TYPE,
                                C2SQueryConfigPermissionPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SQuickToolSwapPayload.TYPE,
                                C2SQuickToolSwapPayload.CODEC);
                PayloadTypeRegistry.serverboundPlay().register(C2SRequestWarehouseSnapshotPayload.TYPE,
                                C2SRequestWarehouseSnapshotPayload.CODEC);
        }

        public static void registerS2CPayloads() {
                PayloadTypeRegistry.clientboundPlay().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
                PayloadTypeRegistry.clientboundPlay().register(S2CConfigPermissionResultPayload.TYPE,
                                S2CConfigPermissionResultPayload.CODEC);
                PayloadTypeRegistry.clientboundPlay().register(S2COpenHopperFilterPayload.TYPE,
                                S2COpenHopperFilterPayload.CODEC);
                PayloadTypeRegistry.clientboundPlay().register(S2COpenFoodFilterPayload.TYPE, S2COpenFoodFilterPayload.CODEC);
                PayloadTypeRegistry.clientboundPlay().register(S2CWarehousePinnedUpdatePayload.TYPE,
                                S2CWarehousePinnedUpdatePayload.CODEC);
                PayloadTypeRegistry.clientboundPlay().register(S2CWarehouseSnapshotPayload.TYPE,
                                S2CWarehouseSnapshotPayload.CODEC);
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
                ServerPlayNetworking.registerGlobalReceiver(C2SQuickToolSwapPayload.TYPE,
                                ModServerNetworking::handleQuickToolSwap);
                ServerPlayNetworking.registerGlobalReceiver(C2SRequestWarehouseSnapshotPayload.TYPE,
                                ModServerNetworking::handleRequestWarehouseSnapshot);
        }
}
