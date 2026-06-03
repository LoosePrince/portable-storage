package com.portablestorage;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.client.gui.WarehouseWidget;
import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.ModClientNetworking;
import com.portablestorage.network.OpenCraftingPayload;
import com.portablestorage.screen.BoundBarrelScreen;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.upgrade.WorkbenchUpgrade;
import com.portablestorage.util.WarehouseSetting;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
    private static PlayerWarehouse pendingAutoFoldWarehouse;
    private static int pendingAutoFoldTicks;

    @Override
    public void onInitializeClient() {
        ModClientNetworking.registerClientReceivers();
        MenuScreens.register(ModScreenHandlers.CRAFTING_WAREHOUSE, CraftingWarehouseScreen::new);
        MenuScreens.register(ModScreenHandlers.BOUND_BARREL, BoundBarrelScreen::new);

        var keyCategory = new net.minecraft.client.KeyMapping.Category(
                Identifier.fromNamespaceAndPath(PortableStorage.MOD_ID, "key_category"));

        openCraftingKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portablestorage.open_crafting",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                keyCategory));
        toggleWarehouseFoldKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portablestorage.toggle_warehouse_fold",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                keyCategory));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCraftingKey.consumeClick()) {
                if (client.player != null) {
                    PlayerWarehouse warehouse = ModComponents.get(client.player).getWarehouse(client.player.getUUID());
                    if (warehouse.isEnabled() && !warehouse.getUpgrade(WorkbenchUpgrade.ID).isEmpty()) {
                        ClientPlayNetworking.send(new OpenCraftingPayload());
                    }
                }
            }
            while (toggleWarehouseFoldKey.consumeClick()) {
                tryToggleWarehouseFold(client);
            }
            flushPendingAutoFold(client);
            if (client.screen instanceof WarehouseScreen s) {
                WarehouseWidget w = s.portablestorage$getWarehouseWidget();
                if (w != null) {
                    w.updateFrozenMode();
                }
            }
        });

        EntityRenderers.register(com.portablestorage.entity.ModEntities.RIFT_AVATAR,
                context -> new net.minecraft.client.renderer.entity.EntityRenderer<com.portablestorage.entity.RiftAvatarEntity, net.minecraft.client.renderer.entity.state.EntityRenderState>(
                        context) {
                    @Override
                    public net.minecraft.client.renderer.entity.state.EntityRenderState createRenderState() {
                        return new net.minecraft.client.renderer.entity.state.EntityRenderState();
                    }
                });
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
        ClientPlayNetworking.send(new com.portablestorage.network.C2SUpdateWarehouseStatePayload(
                Optional.empty(),
                Optional.empty(),
                Optional.of(WarehouseSetting.FOLD.ordinal()),
                Optional.of(folded ? 1 : 0),
                Optional.empty(),
                Optional.empty()));
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
        ClientPlayNetworking.send(new com.portablestorage.network.C2SUpdateWarehouseStatePayload(
                Optional.empty(),
                Optional.empty(),
                Optional.of(WarehouseSetting.FOLD.ordinal()),
                Optional.of(1),
                Optional.empty(),
                Optional.empty()));
        pendingAutoFoldWarehouse = null;
        pendingAutoFoldTicks = 0;
    }
}
