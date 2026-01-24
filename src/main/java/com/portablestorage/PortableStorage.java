package com.portablestorage;

import com.portablestorage.command.PortableStorageCommand;
import com.portablestorage.config.ModConfig;
import com.portablestorage.event.PlayerDeathEventHandler;
import com.portablestorage.item.ModItems;
import com.portablestorage.network.ModNetworking;
import com.portablestorage.network.SyncConfigPayload;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.upgrade.HopperUpgrade;
import com.portablestorage.upgrade.TrashCanUpgrade;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 便携式存储模组主类
 * 负责模组初始化、注册物品、方块、实体和事件处理器
 */
public class PortableStorage implements ModInitializer {
	public static final String MOD_ID = "portablestorage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 创建模组资源定位符
     * @param path 资源路径
     * @return ResourceLocation 实例
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
	public void onInitialize() {
        ModConfig.load();
        com.portablestorage.block.ModBlocks.registerModBlocks();
        com.portablestorage.block.entity.ModBlockEntities.registerModBlockEntities();
        com.portablestorage.entity.ModEntities.registerModEntities();
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(com.portablestorage.entity.ModEntities.RIFT_AVATAR, com.portablestorage.entity.RiftAvatarEntity.createAttributes());
        ModItems.registerModItems();
        
        // 注册升级
        com.portablestorage.upgrade.UpgradeRegistry.register(new TrashCanUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.WorkbenchUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new HopperUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.BarrelUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.RiftUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.SpectralArrowUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.BedUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.ExperienceUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.PistonUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.EnchantedGoldenAppleUpgrade());
        
        ModScreenHandlers.register();
        ModNetworking.registerC2SPayloads();
        ModNetworking.registerS2CPayloads();
        ModNetworking.registerServerReceivers();
        PlayerDeathEventHandler.register();
        com.portablestorage.event.WarehouseActivationHandler.register();
        com.portablestorage.event.SpaceRiftEventHandler.register();
        
        // 注册命令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PortableStorageCommand.register(dispatcher);
        });
        
        // 玩家加入时重置裂隙边界并同步配置
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            var warehouse = com.portablestorage.component.ModComponents.get(player).getWarehouse(player.getUUID());
            warehouse.setOwnerName(player.getScoreboardName());

            // 强制加载区块
            com.portablestorage.world.SpaceRiftManager.updatePlotForcedLoading(player, warehouse, true);

            if (player.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                // 移除在线时的复制体
                com.portablestorage.world.SpaceRiftManager.removeAvatar(player);

                // 在下一刻或几秒后发送，确保客户端已经进入维度
                server.execute(() -> {
                    com.portablestorage.world.SpaceRiftManager.applyPersonalBorder(player, warehouse);
                });
            }

            sender.sendPacket(new SyncConfigPayload(
                ModConfig.enable3x3Crafting, 
                ModConfig.dropStorageOnDeath,
                ModConfig.allowHotReload,
                ModConfig.maxStorageTypes,
                ModConfig.maxItemStackSize,
                ModConfig.baseMaxStorageTypes,
                ModConfig.baseMaxItemStackSize,
                ModConfig.maxItemNbtSize,
                ModConfig.unconditionalWarehouse,
                ModConfig.hopperRange,
                ModConfig.hopperFrequency,
                ModConfig.lavaInfiniteThreshold,
                ModConfig.waterInfiniteThreshold,
                ModConfig.riftUpgradeItem,
                ModConfig.riftChunkSize,
                ModConfig.enableRiftForcedLoading,
                ModConfig.riftForcedLoadingRange
            ));
        });

        // 升级系统服务端 Tick 处理（漏斗、裂隙等）
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
            
            // 处理丢出任务
            PortableStorageCommand.tickDropTasks(server);
        });

        // 玩家登出时清空垃圾桶升级中的物品并停止强制加载
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (player != null) {
                var warehouse = com.portablestorage.component.ModComponents.get(player).getWarehouse(player.getUUID());
                
                // 停止强制加载
                com.portablestorage.world.SpaceRiftManager.updatePlotForcedLoading(player, warehouse, false);

                // 如果在裂隙维度，创建复制体
                if (player.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                    com.portablestorage.world.SpaceRiftManager.spawnAvatar(player, warehouse);
                }

                if (!warehouse.getUpgrade(TrashCanUpgrade.ID).isEmpty()) {
                    warehouse.setUpgrade(TrashCanUpgrade.ID, ItemStack.EMPTY);
                }
            }
        });

        LOGGER.info("Portable Storage Initialized!");
	}
}
