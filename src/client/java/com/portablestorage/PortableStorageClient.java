package com.portablestorage;

import com.portablestorage.network.ModClientNetworking;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.BoundBarrelScreen;
import com.portablestorage.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;

public class PortableStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModClientNetworking.registerClientReceivers();
		MenuScreens.register(ModScreenHandlers.CRAFTING_WAREHOUSE, CraftingWarehouseScreen::new);
		MenuScreens.register(ModScreenHandlers.BOUND_BARREL, BoundBarrelScreen::new);

		// 注册复制体渲染器
		EntityRendererRegistry.register(com.portablestorage.entity.ModEntities.RIFT_AVATAR, (context) -> new EntityRenderer<com.portablestorage.entity.RiftAvatarEntity>(context) {
			@Override
			public ResourceLocation getTextureLocation(com.portablestorage.entity.RiftAvatarEntity entity) {
				return ResourceLocation.withDefaultNamespace("textures/entity/steve.png");
			}
		});
	}
}
