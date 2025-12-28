package com.portablestorage;

import com.portablestorage.config.ModConfig;
import com.portablestorage.event.PlayerDeathEventHandler;
import com.portablestorage.item.ModItems;
import com.portablestorage.network.ModNetworking;
import com.portablestorage.network.SyncConfigPayload;
import com.portablestorage.screen.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortableStorage implements ModInitializer {
	public static final String MOD_ID = "portablestorage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        ModConfig.load();
        ModItems.registerModItems();
        ModScreenHandlers.register();
        ModNetworking.registerC2SPayloads();
        ModNetworking.registerS2CPayloads();
        ModNetworking.registerServerReceivers();
        PlayerDeathEventHandler.register();

        // 玩家加入时同步服务端配置
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new SyncConfigPayload(ModConfig.enable3x3Crafting, ModConfig.dropStorageOnDeath));
        });

        LOGGER.info("Portable Storage Initialized!");
	}
}
