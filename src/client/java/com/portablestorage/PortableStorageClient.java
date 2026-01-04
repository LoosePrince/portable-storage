package com.portablestorage;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;

public class PortableStorageClient implements ClientModInitializer {
    public static KeyMapping openCraftingKey;

	@Override
	public void onInitializeClient() {
		ModClientNetworking.registerClientReceivers();
		MenuScreens.register(ModScreenHandlers.CRAFTING_WAREHOUSE, CraftingWarehouseScreen::new);
		MenuScreens.register(ModScreenHandlers.BOUND_BARREL, BoundBarrelScreen::new);

		// 注册快捷键
        openCraftingKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.portablestorage.open_crafting",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), // 默认不设置
            "key.categories.portablestorage"
        ));

        // 快捷键监听
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCraftingKey.consumeClick()) {
                if (client.player != null) {
                    PlayerWarehouse warehouse = ModComponents.get(client.player).getWarehouse(client.player.getUUID());
                    if (warehouse.isEnabled() && !warehouse.getUpgrade(WorkbenchUpgrade.ID).isEmpty()) {
                        ClientPlayNetworking.send(new OpenCraftingPayload());
                    }
                }
            }
        });

		// 注册复制体渲染器
		EntityRendererRegistry.register(com.portablestorage.entity.ModEntities.RIFT_AVATAR, (context) -> new EntityRenderer<com.portablestorage.entity.RiftAvatarEntity>(context) {
			@Override
			public ResourceLocation getTextureLocation(com.portablestorage.entity.RiftAvatarEntity entity) {
				return ResourceLocation.withDefaultNamespace("textures/entity/steve.png");
			}
		});
	}
}
