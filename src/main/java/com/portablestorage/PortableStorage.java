package com.portablestorage;

import com.portablestorage.config.ModConfig;
import com.portablestorage.event.PlayerDeathEventHandler;
import com.portablestorage.item.ModItems;
import com.portablestorage.network.ModNetworking;
import com.portablestorage.network.SyncConfigPayload;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.upgrade.TrashCanUpgrade;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortableStorage implements ModInitializer {
	public static final String MOD_ID = "portablestorage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
	public void onInitialize() {
        ModConfig.load();
        ModItems.registerModItems();
        
        // 注册升级
        com.portablestorage.upgrade.UpgradeRegistry.register(new TrashCanUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.WorkbenchUpgrade());
        
        ModScreenHandlers.register();
        ModNetworking.registerC2SPayloads();
        ModNetworking.registerS2CPayloads();
        ModNetworking.registerServerReceivers();
        PlayerDeathEventHandler.register();
        com.portablestorage.event.WarehouseActivationHandler.register();
        
        // 玩家加入时同步服务端配置
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new SyncConfigPayload(
                ModConfig.enable3x3Crafting, 
                ModConfig.dropStorageOnDeath,
                ModConfig.allowHotReload,
                ModConfig.maxStorageTypes,
                ModConfig.maxItemStackSize,
                ModConfig.baseMaxStorageTypes,
                ModConfig.baseMaxItemStackSize,
                ModConfig.unconditionalWarehouse
            ));
        });

        // 玩家登出时清空垃圾桶升级中的物品
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (player != null) {
                var warehouse = com.portablestorage.component.ModComponents.get(player).getWarehouse(player.getUUID());
                if (!warehouse.getUpgrade(TrashCanUpgrade.ID).isEmpty()) {
                    warehouse.setUpgrade(TrashCanUpgrade.ID, ItemStack.EMPTY);
                }
            }
        });

        LOGGER.info("Portable Storage Initialized!");
	}
}
