package com.portable.storage;

import com.portable.storage.block.ModBlocks;
import com.portable.storage.blockentity.ModBlockEntities;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.net.NetworkChannels;

public class PortableStorage implements ModInitializer {
	public static final String MOD_ID = "portable-storage";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// 注册自定义方块和方块实体
		ModBlocks.register();
		ModBlockEntities.register();

		NetworkChannels.registerCodecs();
		ServerNetworkingHandlers.register();
		
		LOGGER.info("Portable Storage initialized");
	}
}