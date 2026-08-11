package com.portablestorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.portablestorage.command.PortableStorageCommand;
import com.portablestorage.config.ModConfig;
import com.portablestorage.event.PlayerDeathEventHandler;
import com.portablestorage.item.ModItems;
import com.portablestorage.network.ModNetworking;
import com.portablestorage.network.SyncConfigPayload;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.upgrade.HopperUpgrade;
import com.portablestorage.upgrade.TrashCanUpgrade;
import com.portablestorage.util.FakePlayerUtils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;

public class PortableStorage implements ModInitializer {
    public static final String MOD_ID = "portablestorage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModConfig.load();
        com.portablestorage.block.ModBlocks.registerModBlocks();
        com.portablestorage.block.entity.ModBlockEntities.registerModBlockEntities();
        com.portablestorage.entity.ModEntities.registerModEntities();
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                com.portablestorage.entity.ModEntities.RIFT_AVATAR,
                com.portablestorage.entity.RiftAvatarEntity.createAttributes());
        ModItems.registerModItems();

        // Register Upgrades
        com.portablestorage.upgrade.UpgradeRegistry.register(new TrashCanUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.WorkbenchUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new HopperUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.BarrelUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.RiftUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.SpectralArrowUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.BedUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.ExperienceUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.PistonUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.ToolUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry
                .register(new com.portablestorage.upgrade.EnchantedGoldenAppleUpgrade());
        com.portablestorage.upgrade.UpgradeRegistry.register(new com.portablestorage.upgrade.ConduitUpgrade());

        ModScreenHandlers.register();
        ModNetworking.registerC2SPayloads();
        ModNetworking.registerS2CPayloads();
        ModNetworking.registerServerReceivers();
        PlayerDeathEventHandler.register();
        com.portablestorage.event.WarehouseActivationHandler.register();
        com.portablestorage.event.SpaceRiftEventHandler.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PortableStorageCommand.register(dispatcher);
        });

        // Player Join Event - Ignore Fake Players
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            if (FakePlayerUtils.isFakePlayer(player)) {
                return;
            }

            var warehouse = com.portablestorage.storage.service.WarehouseService.get(player);
            if (warehouse == null) return;

            com.portablestorage.storage.service.WarehouseService.commitIfWarehouseChanged(player, warehouse,
                    "player_join.runtime_state", () -> {
                        warehouse.setOwnerName(player.getScoreboardName());

                        if (player.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                            warehouse.setRiftBorderResendTicks(40);
                        }
                        return null;
                    });

            com.portablestorage.world.SpaceRiftManager.updatePlotForcedLoading(player, warehouse, true);

            if (player.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                com.portablestorage.world.SpaceRiftManager.removeAvatar(player);
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
                    ModConfig.baseWarehouseActivationItem,
                    ModConfig.fullWarehouseActivationItem,
                    ModConfig.hopperRange,
                    ModConfig.hopperFrequency,
                    ModConfig.lavaInfiniteThreshold,
                    ModConfig.waterInfiniteThreshold,
                    ModConfig.riftUpgradeItem,
                    ModConfig.riftChunkSize,
                    ModConfig.riftPlotSpacingChunks,
                    ModConfig.riftFloorY,
                    ModConfig.enableRiftForcedLoading,
                    ModConfig.riftForcedLoadingRange,
                    ModConfig.enableRiftAvatar,
                    ModConfig.enableRiftBorder,
                    ModConfig.riftBorderWarningBlocks,
                    ModConfig.enableConduitUpgrade));

            boolean canEdit = ModConfig.allowHotReload
                    && server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
            sender.sendPacket(new com.portablestorage.network.S2CConfigPermissionResultPayload(canEdit));

            server.execute(() -> com.portablestorage.storage.service.WarehouseService.sync(player));
        });

        // Server Tick Event - Ignore Fake Players
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (FakePlayerUtils.isFakePlayer(player)) {
                    continue;
                }
                var warehouse = com.portablestorage.storage.service.WarehouseService.get(player);
                if (warehouse != null && warehouse.isEnabled()) {
                    for (java.util.Map.Entry<Identifier, ItemStack> entry : warehouse.getUpgradeStorage().entrySet()) {
                        com.portablestorage.upgrade.UpgradeType type = com.portablestorage.upgrade.UpgradeRegistry
                                .get(entry.getKey());
                        if (type != null && type.requiresServerTick()) {
                            com.portablestorage.storage.service.WarehouseService.commitIfWarehouseChanged(player, warehouse,
                                    "upgrade_tick." + entry.getKey(), () -> {
                                        type.serverTick(warehouse, player);
                                        return null;
                                    });
                        }
                    }
                }
            }

            PortableStorageCommand.tickDropTasks(server);
        });

        // Disconnect Event - Ignore Fake Players
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (player != null) {
                if (FakePlayerUtils.isFakePlayer(player)) {
                    return;
                }
                var warehouse = com.portablestorage.storage.service.WarehouseService.get(player);
                if (warehouse == null) return;

                com.portablestorage.world.SpaceRiftManager.updatePlotForcedLoading(player, warehouse, false);

                if (player.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                    com.portablestorage.world.SpaceRiftManager.spawnAvatar(player, warehouse);
                }

                if (!warehouse.getUpgrade(TrashCanUpgrade.ID).isEmpty()) {
                    com.portablestorage.storage.service.WarehouseService.commitIfWarehouseChanged(player, warehouse,
                            "player_disconnect.clear_trash", () -> {
                                warehouse.setUpgrade(TrashCanUpgrade.ID, ItemStack.EMPTY);
                                return null;
                            });
                }
            }
        });

        LOGGER.info("Portable Storage Initialized!");
    }
}