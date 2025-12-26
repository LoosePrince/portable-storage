package com.portablestorage;

import com.portablestorage.config.ModConfig;
import com.portablestorage.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortableStorage implements ModInitializer {
    public static final String MOD_ID = "portablestorage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModNetworking.registerC2SPayloads();
        ModNetworking.registerServerReceivers();

        LOGGER.info("Portable Storage Initialized!");
    }
}
