package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;

/**
 * 网络注册类
 * 负责注册客户端到服务端和服务端到客户端的网络数据包类型和接收器
 */
public class ModNetworking {
    public static void registerC2SPayloads() {
        // 在 1.20.1 中，不需要注册 Payload，直接注册接收器即可
    }

    public static void registerS2CPayloads() {
        // 在 1.20.1 中，不需要注册 Payload，直接注册接收器即可
    }

    public static void registerServerReceivers() {
        // 在 1.20.1 中，使用 ResourceLocation 和 PlayChannelHandler
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("quick_transfer"), (server, player, handler, buf, responseSender) -> {
            QuickTransferPayload payload = new QuickTransferPayload(buf);
            ModServerNetworking.handleQuickTransfer(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("open_crafting"), (server, player, handler, buf, responseSender) -> {
            OpenCraftingPayload payload = new OpenCraftingPayload(buf);
            ModServerNetworking.handleOpenCrafting(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("update_hopper_filters"), (server, player, handler, buf, responseSender) -> {
            C2SUpdateHopperFiltersPayload payload = new C2SUpdateHopperFiltersPayload(buf);
            ModServerNetworking.handleUpdateHopperFilters(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("update_food_filters"), (server, player, handler, buf, responseSender) -> {
            C2SUpdateFoodFiltersPayload payload = new C2SUpdateFoodFiltersPayload(buf);
            ModServerNetworking.handleUpdateFoodFilters(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("toggle_pinned"), (server, player, handler, buf, responseSender) -> {
            C2STogglePinnedPayload payload = new C2STogglePinnedPayload(buf);
            ModServerNetworking.handleTogglePinned(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("recipe_transfer"), (server, player, handler, buf, responseSender) -> {
            C2SRecipeTransferPayload payload = new C2SRecipeTransferPayload(buf);
            ModServerNetworking.handleRecipeTransfer(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("update_forbidden_players"), (server, player, handler, buf, responseSender) -> {
            C2SUpdateForbiddenPlayersPayload payload = new C2SUpdateForbiddenPlayersPayload(buf);
            ModServerNetworking.handleUpdateForbiddenPlayers(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("drop_warehouse_item"), (server, player, handler, buf, responseSender) -> {
            C2SDropWarehouseItemPayload payload = new C2SDropWarehouseItemPayload(buf);
            ModServerNetworking.handleDropWarehouseItem(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("update_frozen_state"), (server, player, handler, buf, responseSender) -> {
            C2SUpdateFrozenStatePayload payload = new C2SUpdateFrozenStatePayload(buf);
            ModServerNetworking.handleUpdateFrozenState(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("double_click_quick_store"), (server, player, handler, buf, responseSender) -> {
            C2SDoubleClickQuickStorePayload payload = new C2SDoubleClickQuickStorePayload(buf);
            ModServerNetworking.handleDoubleClickQuickStore(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("update_state"), (server, player, handler, buf, responseSender) -> {
            C2SUpdateWarehouseStatePayload payload = new C2SUpdateWarehouseStatePayload(buf);
            ModServerNetworking.handleUpdateWarehouseState(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("refill"), (server, player, handler, buf, responseSender) -> {
            RefillPayload payload = new RefillPayload(buf);
            ModServerNetworking.handleRefill(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("update_server_config"), (server, player, handler, buf, responseSender) -> {
            UpdateServerConfigPayload payload = new UpdateServerConfigPayload(buf);
            ModServerNetworking.handleUpdateServerConfig(server, player, payload, responseSender);
        });
        ServerPlayNetworking.registerGlobalReceiver(PortableStorage.id("upgrade_interaction"), (server, player, handler, buf, responseSender) -> {
            C2SUpgradeInteractionPayload payload = new C2SUpgradeInteractionPayload(buf);
            ModServerNetworking.handleUpgradeInteraction(server, player, payload, responseSender);
        });
    }
}

