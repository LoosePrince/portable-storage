package com.portablestorage;

import com.portablestorage.network.ModClientNetworking;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class PortableStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModClientNetworking.registerClientReceivers();
		MenuScreens.register(ModScreenHandlers.CRAFTING_WAREHOUSE, CraftingWarehouseScreen::new);
	}
}
