package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.item.StorageKeyItem;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.player.PlayerStorageAccess;
import com.portable.storage.world.SpaceRiftManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * 处理玩家死亡和复活事件，确保仓库数据在死亡时正确保存
 */
public class PlayerDeathEventHandler {
    
    public static void register() {
        // 在玩家复活后恢复仓库数据
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer == null || newPlayer == null) return;
            
            try {
                // 获取旧玩家的仓库数据
                PlayerStorageAccess oldAccess = (PlayerStorageAccess) oldPlayer;
                PlayerStorageAccess newAccess = (PlayerStorageAccess) newPlayer;
                
                // 复制仓库数据到新玩家
                if (oldAccess.portableStorage$getInventory() != null) {
                    newAccess.portableStorage$setInventory(oldAccess.portableStorage$getInventory());
                }
                
                // 复制升级数据到新玩家
                if (oldAccess.portableStorage$getUpgradeInventory() != null) {
                    newAccess.portableStorage$setUpgradeInventory(oldAccess.portableStorage$getUpgradeInventory());
                }
                
                // 注意：不在这里复制启用状态，因为 COPY_FROM 事件已经处理了死亡掉落逻辑
                // 启用状态应该在 COPY_FROM 事件中根据游戏规则正确设置
                
                // 同步数据到客户端
                ServerNetworkingHandlers.sendSync(newPlayer);
                
                PortableStorage.LOGGER.info("Restored portable storage data for player {} after respawn", newPlayer.getName().getString());
                
            } catch (Exception e) {
                PortableStorage.LOGGER.error("Failed to restore portable storage data for player {} after respawn", newPlayer.getName().getString(), e);
            }
        });
        
        // 在玩家死亡前保存数据（作为备用方案）
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer == null || newPlayer == null) return;
            
            try {
                // 获取旧玩家的仓库数据
                PlayerStorageAccess oldAccess = (PlayerStorageAccess) oldPlayer;
                PlayerStorageAccess newAccess = (PlayerStorageAccess) newPlayer;
                
                ServerConfig config = ServerConfig.getInstance();
                boolean requireCondition = config.isRequireConditionToEnable();
                boolean wasEnabled = oldAccess.portableStorage$isStorageEnabled();

                if (!requireCondition) {
                    newAccess.portableStorage$setStorageEnabled(true);
                } else if (wasEnabled) {
                    // 检查游戏规则：keepInventory
                    boolean keepInventory = newPlayer.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.KEEP_INVENTORY);
                    
                    if (!keepInventory) {
                        // 死亡掉落规则开启时，将仓库状态设为未激活（但保留数据）
                        newAccess.portableStorage$setStorageEnabled(false);
                        
                        // 检查是否在裂隙中死亡
                        if (oldPlayer.getWorld().getRegistryKey() == SpaceRiftManager.DIMENSION_KEY) {
                            // 在裂隙中死亡：钥匙掉落在进入裂隙前的位置
                            dropStorageKeyAtReturnPoint(oldPlayer);
                        } else {
                            // 在其他维度死亡：在死亡位置掉落钥匙
                            dropStorageKeyAtPosition(oldPlayer, oldPlayer.getPos());
                        }
                        
                        PortableStorage.LOGGER.info("Player {} died with storage enabled, dropped storage key", oldPlayer.getName().getString());
                    } else {
                        // 死亡不掉落规则开启时，保持仓库激活状态
                        newAccess.portableStorage$setStorageEnabled(true);
                        PortableStorage.LOGGER.info("Player {} died with keepInventory enabled, storage remains active", oldPlayer.getName().getString());
                    }
                }
                
                // 复制仓库数据到新玩家
                if (oldAccess.portableStorage$getInventory() != null) {
                    newAccess.portableStorage$setInventory(oldAccess.portableStorage$getInventory());
                }
                
                // 复制升级数据到新玩家
                if (oldAccess.portableStorage$getUpgradeInventory() != null) {
                    newAccess.portableStorage$setUpgradeInventory(oldAccess.portableStorage$getUpgradeInventory());
                }
                
                PortableStorage.LOGGER.info("Copied portable storage data for player {} during respawn", newPlayer.getName().getString());
                
            } catch (Exception e) {
                PortableStorage.LOGGER.error("Failed to copy portable storage data for player {} during respawn", newPlayer.getName().getString(), e);
            }
        });
    }
    
    /**
     * 在玩家进入裂隙前的位置掉落仓库钥匙
     */
    private static void dropStorageKeyAtReturnPoint(ServerPlayerEntity player) {
        try {
            // 获取返回点位置
            net.minecraft.util.math.GlobalPos returnPoint = SpaceRiftManager.getReturnPoint(player.getUuid());
            
            if (returnPoint != null) {
                // 获取返回点所在的世界
                net.minecraft.server.world.ServerWorld returnWorld = player.getServer().getWorld(returnPoint.dimension());
                if (returnWorld != null) {
                    // 在返回点位置掉落钥匙
                    net.minecraft.util.math.Vec3d returnPos = net.minecraft.util.math.Vec3d.ofCenter(returnPoint.pos());
                    dropStorageKeyAtPosition(player, returnWorld, returnPos);
                    
                    PortableStorage.LOGGER.info("Dropped storage key for player {} at return point {}", 
                            player.getName().getString(), returnPoint);
                } else {
                    // 返回点世界不存在，在主世界出生点掉落
                    net.minecraft.server.world.ServerWorld overworld = player.getServer().getOverworld();
                    net.minecraft.util.math.BlockPos spawn = overworld.getSpawnPos();
                    dropStorageKeyAtPosition(player, overworld, net.minecraft.util.math.Vec3d.ofCenter(spawn));
                    
                    PortableStorage.LOGGER.info("Return world not found, dropped storage key for player {} at overworld spawn", 
                            player.getName().getString());
                }
            } else {
                // 没有返回点，在主世界出生点掉落
                net.minecraft.server.world.ServerWorld overworld = player.getServer().getOverworld();
                net.minecraft.util.math.BlockPos spawn = overworld.getSpawnPos();
                dropStorageKeyAtPosition(player, overworld, net.minecraft.util.math.Vec3d.ofCenter(spawn));
                
                PortableStorage.LOGGER.info("No return point found, dropped storage key for player {} at overworld spawn", 
                        player.getName().getString());
            }
        } catch (Exception e) {
            PortableStorage.LOGGER.error("Failed to drop storage key at return point for player {}", 
                    player.getName().getString(), e);
        }
    }

    /**
     * 在指定位置掉落仓库钥匙
     */
    private static void dropStorageKeyAtPosition(ServerPlayerEntity player, Vec3d position) {
        dropStorageKeyAtPosition(player, player.getServerWorld(), position);
    }

    /**
     * 在指定世界和位置掉落仓库钥匙
     */
    private static void dropStorageKeyAtPosition(ServerPlayerEntity player, ServerWorld world, Vec3d position) {
        try {
            // 创建仓库钥匙
            ItemStack storageKey = StorageKeyItem.createStorageKey(player);
            
            // 创建掉落物实体
            ItemEntity itemEntity = new ItemEntity(world, position.x, position.y, position.z, storageKey);
            
            // 设置掉落物的属性
            itemEntity.setPickupDelay(40); // 2秒后可以拾取
            itemEntity.setVelocity(0, 0.2, 0); // 轻微向上弹起
            
            // 生成掉落物
            world.spawnEntity(itemEntity);
            
            PortableStorage.LOGGER.info("Dropped storage key for player {} at position {}", 
                    player.getName().getString(), position);
                    
        } catch (Exception e) {
            PortableStorage.LOGGER.error("Failed to drop storage key for player {}", 
                    player.getName().getString(), e);
        }
    }
}