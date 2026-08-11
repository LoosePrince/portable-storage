package com.portablestorage.event;

import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.item.StorageKeyItem;
import com.portablestorage.util.FakePlayerUtils;
import com.portablestorage.world.SpaceRiftManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

public class PlayerDeathEventHandler {
    public static void register() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer == null || newPlayer == null)
                return;
            if (FakePlayerUtils.isFakePlayer(oldPlayer) || FakePlayerUtils.isFakePlayer(newPlayer))
                return;

            PlayerWarehouse oldWarehouse = WarehouseService.get(oldPlayer);
            PlayerWarehouse newWarehouse = WarehouseService.get(newPlayer);
            if (oldWarehouse == null || newWarehouse == null)
                return;

            boolean keepInventory = newPlayer.level().getGameRules().get(GameRules.KEEP_INVENTORY);
            boolean isUnconditional = !"NONE".equals(com.portablestorage.config.ModConfig.unconditionalWarehouse);

            if (!keepInventory && ModConfig.dropStorageOnDeath && oldWarehouse.isEnabled() && !isUnconditional) {
                WarehouseService.commitIfWarehouseChanged(newPlayer, newWarehouse, "player_death.disable_warehouse", () -> {
                    newWarehouse.setEnabled(false);
                    newWarehouse.setFolded(true);
                    return null;
                });

                dropKey((ServerPlayer) oldPlayer, oldWarehouse);
            } else {
                WarehouseService.commitIfWarehouseChanged(newPlayer, newWarehouse, "player_death.restore_enabled", () -> {
                    newWarehouse.setEnabled(oldWarehouse.isEnabled());
                    return null;
                });
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (FakePlayerUtils.isFakePlayer(newPlayer))
                return;
            WarehouseService.sync(newPlayer);
        });
    }

    private static void dropKey(ServerPlayer player, PlayerWarehouse warehouse) {
        ItemStack keyStack = StorageKeyItem.create(player);

        Level dropLevel = player.level();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        if (dropLevel.dimension().equals(SpaceRiftManager.DIMENSION_KEY)) {
            Identifier returnDimId = warehouse.getRiftReturnDim();
            BlockPos returnPos = warehouse.getRiftReturnPos();
            if (returnDimId != null && returnPos != null) {
                ServerLevel targetLevel = ((ServerLevel) player.level())
                        .getServer()
                        .getLevel(ResourceKey.create(Registries.DIMENSION, returnDimId));
                if (targetLevel != null) {
                    dropLevel = targetLevel;
                    x = returnPos.getX() + 0.5;
                    y = returnPos.getY() + 0.5;
                    z = returnPos.getZ() + 0.5;
                }
            }
        }

        ItemEntity itemEntity = new ItemEntity(dropLevel, x, y, z, keyStack);
        net.minecraft.world.item.ItemStack entityStack = itemEntity.getItem();
        CustomData customData = entityStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = customData != null ? customData.copyTag()
                : new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("portablestorage_is_warehouse_key", true);
        tag.putString("portablestorage_owner_uuid", player.getUUID().toString());
        entityStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
        itemEntity.setDeltaMovement(0, 0.2, 0);
        itemEntity.setInvulnerable(true);
        itemEntity.setPickUpDelay(40);
        dropLevel.addFreshEntity(itemEntity);
    }
}