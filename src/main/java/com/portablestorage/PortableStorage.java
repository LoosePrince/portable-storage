package com.portablestorage;

import com.portablestorage.component.ModComponents;
import com.portablestorage.config.ModConfig;
import com.portablestorage.network.ChangeRowsPayload;
import com.portablestorage.network.ScrollPayload;
import com.portablestorage.network.SearchPayload;
import com.portablestorage.network.UpdateSettingsPayload;
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
		ModConfig.load();
        PayloadTypeRegistry.playC2S().register(ScrollPayload.TYPE, ScrollPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SearchPayload.TYPE, SearchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChangeRowsPayload.TYPE, ChangeRowsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UpdateSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
                switch (payload.settingType()) {
                    case 0 -> warehouse.setFolded(payload.value() == 1);
                    case 1 -> warehouse.setSortMode(payload.value());
                    case 2 -> warehouse.setAscending(payload.value() == 1);
                    case 3 -> warehouse.setQuickInteraction(payload.value() == 1);
                }
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ChangeRowsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
                warehouse.setVisibleRows(warehouse.getVisibleRows() + payload.delta());
                
                // 数据结构改变（槽位数量改变），必须重新打开或同步菜单
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ScrollPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
                int current = warehouse.getScrollOffset();
                warehouse.setScrollOffset(current - payload.delta());
                
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SearchPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                var warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
                warehouse.setSearchText(payload.searchText());
                
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            });
        });

        LOGGER.info("Portable Storage Initialized!");
	}
}
