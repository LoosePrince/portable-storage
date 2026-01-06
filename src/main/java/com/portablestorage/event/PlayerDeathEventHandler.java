package com.portablestorage.event;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.item.StorageKeyItem;
import com.portablestorage.world.SpaceRiftManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

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
                
                // 掉落钥匙 (如果是裂隙内死亡，则掉落在返回点)
                dropKey((ServerPlayer) oldPlayer, oldWarehouse);
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

    private static void dropKey(ServerPlayer player, PlayerWarehouse warehouse) {
        ItemStack keyStack = StorageKeyItem.create(player);
        
        Level dropLevel = player.level();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // 如果在裂隙维度死亡，且开启了死亡掉落钥匙，
        // 则将钥匙掉落在进入裂隙前的返回点（通常是主世界坐标），防止玩家因失去仓库访问权而导致钥匙永久丢失在裂隙中。
        if (dropLevel.dimension().equals(SpaceRiftManager.DIMENSION_KEY)) {
            ResourceLocation returnDimId = warehouse.getRiftReturnDim();
            BlockPos returnPos = warehouse.getRiftReturnPos();
            if (returnDimId != null && returnPos != null) {
                ServerLevel targetLevel = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, returnDimId));
                if (targetLevel != null) {
                    dropLevel = targetLevel;
                    x = returnPos.getX() + 0.5;
                    y = returnPos.getY() + 0.5;
                    z = returnPos.getZ() + 0.5;
                }
            }
        }
        
        ItemEntity itemEntity = new ItemEntity(dropLevel, x, y, z, keyStack);
        itemEntity.setDeltaMovement(0, 0.2, 0);
        itemEntity.setPickUpDelay(40);
        dropLevel.addFreshEntity(itemEntity);
    }
}

