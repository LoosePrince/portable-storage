package com.portablestorage;

import com.portablestorage.component.ModComponents;
import com.portablestorage.network.ScrollPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortableStorage implements ModInitializer {
    public static final String MOD_ID = "portablestorage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(ScrollPayload.TYPE, ScrollPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ScrollPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                // 改为从世界组件获取特定玩家的仓库
                var warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
                int current = warehouse.getScrollOffset();
                warehouse.setScrollOffset(current - payload.delta());
                
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            });
        });

        LOGGER.info("Portable Storage Initialized!");
    }
}
