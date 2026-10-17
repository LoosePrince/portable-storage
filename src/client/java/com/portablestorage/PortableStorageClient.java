package com.portablestorage;

import com.mojang.blaze3d.platform.InputConstants;
import com.portablestorage.client.gui.QuickToolClientState;
import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.client.gui.WarehouseStateSync;
import com.portablestorage.client.gui.WarehouseWidget;
import com.portablestorage.config.ModConfig;
import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.ModClientNetworking;
import com.portablestorage.network.OpenCraftingPayload;
import com.portablestorage.screen.BoundBarrelScreen;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.upgrade.WorkbenchUpgrade;
import com.portablestorage.util.WarehouseSetting;

import java.util.UUID;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;

public class PortableStorageClient implements ClientModInitializer {
    public static KeyMapping openCraftingKey;
    public static KeyMapping toggleWarehouseFoldKey;
    public static KeyMapping quickToolKey;
    private static PlayerWarehouse pendingAutoFoldWarehouse;
    private static int pendingAutoFoldTicks;
    private static UUID preferencesSyncedFor;
    private static int preferenceSyncDelay;

    @Override
    public void onInitializeClient() {
        ModClientNetworking.registerClientReceivers();
        MenuScreens.register(ModScreenHandlers.CRAFTING_WAREHOUSE, CraftingWarehouseScreen::new);
        MenuScreens.register(ModScreenHandlers.BOUND_BARREL, BoundBarrelScreen::new);
        MenuScreens.register(ModScreenHandlers.TOOL_WAREHOUSE, com.portablestorage.screen.ToolWarehouseScreen::new);

        var keyCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PortableStorage.MOD_ID, "key_category"));

        openCraftingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.portablestorage.open_crafting",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                keyCategory));
        toggleWarehouseFoldKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.portablestorage.toggle_warehouse_fold",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                keyCategory));
        quickToolKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.portablestorage.quick_tool",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                keyCategory));

        // 快捷键监听
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCraftingKey.consumeClick()) {
                if (client.player != null) {
                    PlayerWarehouse warehouse = ModComponents.get(client.player).getWarehouse(client.player.getUUID());
                    if (warehouse.isEnabled() && !warehouse.getUpgrade(WorkbenchUpgrade.ID).isEmpty()) {
                        ClientPlayNetworking.send(new OpenCraftingPayload());
                    } else {
                        // 无权限时静默失败，避免刷日志
                    }
                }
            }
            while (toggleWarehouseFoldKey.consumeClick()) {
                if (!(client.screen instanceof WarehouseScreen)) {
                    tryToggleWarehouseFold(client);
                }
            }
            syncWarehousePreferences(client);
            QuickToolClientState.tick(client);
            flushPendingAutoFold(client);
            if (client.screen instanceof WarehouseScreen s) {
                WarehouseWidget w = s.portablestorage$getWarehouseWidget();
                if (w != null) {
                    w.updateFrozenMode();
                    w.flushDebouncedSearchPacket();
                }
            }
        });

        // 注册复制体渲染器
        EntityRenderers.register(com.portablestorage.entity.ModEntities.RIFT_AVATAR,
                com.portablestorage.client.renderer.RiftAvatarRenderer::new);
    }

    private static void syncWarehousePreferences(Minecraft client) {
        if (client == null || client.player == null) {
            preferencesSyncedFor = null;
            preferenceSyncDelay = 0;
            return;
        }

        UUID playerId = client.player.getUUID();
        if (playerId.equals(preferencesSyncedFor)) {
            return;
        }
        if (preferenceSyncDelay == 0) {
            preferenceSyncDelay = 20;
            return;
        }
        if (--preferenceSyncDelay > 0) {
            return;
        }

        PlayerWarehouse warehouse = ModComponents.get(client.player).getWarehouse(playerId);
        applyAndSendPreference(warehouse, WarehouseSetting.SORT_MODE, ModConfig.warehouseSortMode);
        applyAndSendPreference(warehouse, WarehouseSetting.SORT_ORDER, ModConfig.warehouseAscending ? 1 : 0);
        applyAndSendPreference(warehouse, WarehouseSetting.QUICK_INTERACTION,
                ModConfig.warehouseQuickInteraction ? 1 : 0);
        applyAndSendPreference(warehouse, WarehouseSetting.SMART_COLLAPSE,
                ModConfig.warehouseSmartCollapse ? 1 : 0);
        applyAndSendPreference(warehouse, WarehouseSetting.CRAFT_REFILL, ModConfig.warehouseCraftRefill ? 1 : 0);
        preferencesSyncedFor = playerId;
    }

    private static void applyAndSendPreference(PlayerWarehouse warehouse, WarehouseSetting setting, int value) {
        WarehouseStateSync.applySetting(warehouse, setting, value);
        if (ClientPlayNetworking.canSend(com.portablestorage.network.C2SUpdateWarehouseStatePayload.TYPE)) {
            WarehouseStateSync.sendSetting(setting, value);
        }
    }

    public static boolean matchesToggleWarehouseFoldKey(KeyEvent event) {
        return toggleWarehouseFoldKey != null && toggleWarehouseFoldKey.matches(event);
    }

    public static boolean tryToggleWarehouseFold(Minecraft client) {
        if (client == null || client.player == null || !(client.screen instanceof WarehouseScreen s)) {
            return false;
        }
        WarehouseWidget w = s.portablestorage$getWarehouseWidget();
        if (w == null || !w.shouldShow()) {
            return false;
        }
        PlayerWarehouse warehouse = ModComponents.get(client.player).getWarehouse(client.player.getUUID());
        boolean folded = !warehouse.isFolded();
        warehouse.setFolded(folded);
        WarehouseStateSync.sendSetting(WarehouseSetting.FOLD, folded ? 1 : 0);
        w.refreshAfterFoldChange();
        return true;
    }

    public static void requestAutoFoldAfterScreenClose(PlayerWarehouse warehouse) {
        pendingAutoFoldWarehouse = warehouse;
        pendingAutoFoldTicks = 2;
    }

    private static void flushPendingAutoFold(Minecraft client) {
        if (pendingAutoFoldWarehouse == null) {
            return;
        }
        if (client == null || client.player == null || !ModConfig.autoFoldOnClose || pendingAutoFoldWarehouse.isFolded()) {
            pendingAutoFoldWarehouse = null;
            pendingAutoFoldTicks = 0;
            return;
        }
        if (client.screen instanceof WarehouseScreen) {
            if (--pendingAutoFoldTicks <= 0) {
                pendingAutoFoldWarehouse = null;
                pendingAutoFoldTicks = 0;
            }
            return;
        }
        pendingAutoFoldWarehouse.setFolded(true);
        WarehouseStateSync.sendSetting(WarehouseSetting.FOLD, 1);
        pendingAutoFoldWarehouse = null;
        pendingAutoFoldTicks = 0;
    }
}
