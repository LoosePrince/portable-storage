package com.portablestorage;

import com.mojang.blaze3d.platform.InputConstants;
import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.client.gui.WarehouseWidget;
import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.ModClientNetworking;
import com.portablestorage.network.OpenCraftingPayload;
import com.portablestorage.screen.BoundBarrelScreen;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.upgrade.WorkbenchUpgrade;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;

public class PortableStorageClient implements ClientModInitializer {
    public static KeyMapping openCraftingKey;

    @Override
    public void onInitializeClient() {
        ModClientNetworking.registerClientReceivers();
        MenuScreens.register(ModScreenHandlers.CRAFTING_WAREHOUSE, CraftingWarehouseScreen::new);
        MenuScreens.register(ModScreenHandlers.BOUND_BARREL, BoundBarrelScreen::new);

        openCraftingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.portablestorage.open_crafting",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PortableStorage.MOD_ID, "key_category"))));

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
            if (client.screen instanceof WarehouseScreen s) {
                WarehouseWidget w = s.portablestorage$getWarehouseWidget();
                if (w != null)
                    w.flushDebouncedSearchPacket();
            }
        });

        // 注册复制体渲染器
        EntityRenderers.register(com.portablestorage.entity.ModEntities.RIFT_AVATAR,
                com.portablestorage.client.renderer.RiftAvatarRenderer::new);
    }
}
