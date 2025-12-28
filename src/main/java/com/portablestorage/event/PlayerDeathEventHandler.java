package com.portablestorage.event;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.item.StorageKeyItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

public class PlayerDeathEventHandler {
    public static void register() {
        // 当玩家死亡复活或从末地返回时触发 (COPY_FROM 会在 respawn 之前调用)
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer == null || newPlayer == null) return;

            // 获取仓库组件
            PlayerWarehouse oldWarehouse = ModComponents.WAREHOUSE.get(oldPlayer.level()).getWarehouse(oldPlayer.getUUID());
            PlayerWarehouse newWarehouse = ModComponents.WAREHOUSE.get(newPlayer.level()).getWarehouse(newPlayer.getUUID());

            // 检查 keepInventory 规则
            boolean keepInventory = newPlayer.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

            if (!keepInventory && ModConfig.dropStorageOnDeath && oldWarehouse.isEnabled()) {
                // 如果不保留物品且配置了死亡禁用，且原本是启用的：禁用新玩家的仓库并掉落钥匙
                newWarehouse.setEnabled(false);
                newWarehouse.setFolded(true);
                
                // 在死亡位置掉落钥匙
                dropKey((ServerPlayer) oldPlayer);
            } else {
                // 否则保持（或恢复）启用状态
                newWarehouse.setEnabled(oldWarehouse.isEnabled());
            }
        });
    }

    private static void dropKey(ServerPlayer player) {
        ItemStack keyStack = StorageKeyItem.create(player);
        ItemEntity itemEntity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), keyStack);
        itemEntity.setDeltaMovement(0, 0.2, 0);
        itemEntity.setPickUpDelay(40);
        player.level().addFreshEntity(itemEntity);
    }
}

