package com.portablestorage;

import com.portablestorage.network.ModClientNetworking;
import net.fabricmc.api.ClientModInitializer;

public class PortableStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModClientNetworking.registerClientReceivers();
	}
}
