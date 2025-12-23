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
                var warehouse = ModComponents.WAREHOUSE.get(player);
                int current = warehouse.getScrollOffset();
                warehouse.setScrollOffset(current - payload.delta());
                
                // 关键修复：强制刷新玩家当前的容器菜单，确保槽位物品即时同步
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            });
        });

        LOGGER.info("Portable Storage Initialized!");
    }
}
