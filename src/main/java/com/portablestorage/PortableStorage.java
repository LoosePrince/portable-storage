package com.portablestorage;

import com.portablestorage.config.ModConfig;
import com.portablestorage.event.PlayerDeathEventHandler;
import com.portablestorage.item.ModItems;
import com.portablestorage.network.ModNetworking;
import com.portablestorage.network.SyncConfigPayload;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.upgrade.HopperUpgrade;
import com.portablestorage.upgrade.TrashCanUpgrade;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
        com.portablestorage.block.ModBlocks.registerModBlocks();
        com.portablestorage.block.entity.ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
        
        // 注册升级
        com.portablestorage.upgrade.UpgradeRegistry.register(new TrashCanUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.WorkbenchUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new HopperUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.BarrelUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.RiftUpgrade());
        
        ModScreenHandlers.register();
        ModNetworking.registerC2SPayloads();
        ModNetworking.registerS2CPayloads();
        ModNetworking.registerServerReceivers();
        PlayerDeathEventHandler.register();
        com.portablestorage.event.WarehouseActivationHandler.register();
        com.portablestorage.event.SpaceRiftEventHandler.register();
        
        // 玩家加入时重置裂隙边界 (延迟几秒发送以确保客户端加载完成)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            if (player.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                // 在下一刻或几秒后发送，确保客户端已经进入维度
                server.execute(() -> {
                    var warehouse = com.portablestorage.component.ModComponents.get(player).getWarehouse(player.getUUID());
                    com.portablestorage.world.SpaceRiftManager.applyPersonalBorder(player, warehouse);
                });
            }
        });
        
        // 漏斗升级 Tick 处理
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                var warehouse = com.portablestorage.component.ModComponents.get(player).getWarehouse(player.getUUID());
                if (warehouse.isEnabled()) {
                    for (java.util.Map.Entry<ResourceLocation, ItemStack> entry : warehouse.getUpgradeStorage().entrySet()) {
                        com.portablestorage.upgrade.UpgradeType type = com.portablestorage.upgrade.UpgradeRegistry.get(entry.getKey());
                        if (type != null) {
                            type.serverTick(warehouse, player);
                        }
                    }
                }
            }
        });

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
                ModConfig.unconditionalWarehouse,
                ModConfig.hopperRange,
                ModConfig.hopperFrequency,
                ModConfig.riftUpgradeItem,
                ModConfig.riftChunkSize
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
