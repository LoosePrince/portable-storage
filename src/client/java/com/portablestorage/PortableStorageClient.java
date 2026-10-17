package com.portablestorage;

import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;
import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.C2SUpdateWarehouseStatePayload;
import com.portablestorage.network.ModClientNetworking;
import com.portablestorage.network.OpenCraftingPayload;
import com.portablestorage.util.WarehouseSetting;
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
    private static UUID preferencesSyncedFor;
    private static int preferenceSyncDelay;

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
            syncWarehousePreferences(client);
        });

		// 注册复制体渲染器
		EntityRendererRegistry.register(com.portablestorage.entity.ModEntities.RIFT_AVATAR, (context) -> new EntityRenderer<com.portablestorage.entity.RiftAvatarEntity>(context) {
			@Override
			public ResourceLocation getTextureLocation(com.portablestorage.entity.RiftAvatarEntity entity) {
				return new ResourceLocation("minecraft", "textures/entity/steve.png");
			}
		});
	}

    private static void syncWarehousePreferences(net.minecraft.client.Minecraft client) {
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
        switch (setting) {
            case SORT_MODE -> warehouse.setSortMode(value);
            case SORT_ORDER -> warehouse.setAscending(value == 1);
            case QUICK_INTERACTION -> warehouse.setQuickInteraction(value == 1);
            case SMART_COLLAPSE -> warehouse.setSmartCollapse(value == 1);
            case CRAFT_REFILL -> warehouse.setCraftRefill(value == 1);
            case FOLD -> warehouse.setFolded(value == 1);
        }
        ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(
                Optional.empty(), Optional.empty(), Optional.of(setting.ordinal()), Optional.of(value),
                Optional.empty(), Optional.empty()));
    }
}
