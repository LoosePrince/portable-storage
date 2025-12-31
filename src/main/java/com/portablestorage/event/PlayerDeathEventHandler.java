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
            PlayerWarehouse oldWarehouse = ModComponents.get(oldPlayer).getWarehouse(oldPlayer.getUUID());
            PlayerWarehouse newWarehouse = ModComponents.get(newPlayer).getWarehouse(newPlayer.getUUID());

            // 检查 keepInventory 规则
            boolean keepInventory = newPlayer.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            // 检查无条件开启配置 (只有 NONE 时才允许死亡禁用)
            boolean isUnconditional = !"NONE".equals(com.portablestorage.config.ModConfig.unconditionalWarehouse);

            if (!keepInventory && ModConfig.dropStorageOnDeath && oldWarehouse.isEnabled() && !isUnconditional) {
                // 如果不保留物品、配置了死亡禁用、原本启用、且不是“无条件开启”：禁用并掉落钥匙
                newWarehouse.setEnabled(false);
                newWarehouse.setFolded(true);
                
                // 在死亡位置掉落钥匙
                dropKey((ServerPlayer) oldPlayer);
            } else {
                // 否则保持（或恢复）启用状态
                newWarehouse.setEnabled(oldWarehouse.isEnabled());
            }
        });

        // 重生后强制补发一次全量同步，确保客户端状态绝对准确
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ModComponents.WAREHOUSE.sync(newPlayer.level().getScoreboard());
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

