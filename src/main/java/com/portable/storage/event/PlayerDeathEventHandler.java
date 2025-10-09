package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import com.portable.storage.player.PlayerStorageAccess;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;

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
                
                // 复制启用状态到新玩家
                newAccess.portableStorage$setStorageEnabled(oldAccess.portableStorage$isStorageEnabled());
                
                // 同步数据到客户端
                com.portable.storage.net.ServerNetworkingHandlers.sendSync(newPlayer);
                
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
                
                // 复制仓库数据到新玩家
                if (oldAccess.portableStorage$getInventory() != null) {
                    newAccess.portableStorage$setInventory(oldAccess.portableStorage$getInventory());
                }
                
                // 复制升级数据到新玩家
                if (oldAccess.portableStorage$getUpgradeInventory() != null) {
                    newAccess.portableStorage$setUpgradeInventory(oldAccess.portableStorage$getUpgradeInventory());
                }
                
                // 复制启用状态到新玩家
                newAccess.portableStorage$setStorageEnabled(oldAccess.portableStorage$isStorageEnabled());
                
                PortableStorage.LOGGER.info("Copied portable storage data for player {} during respawn", newPlayer.getName().getString());
                
            } catch (Exception e) {
                PortableStorage.LOGGER.error("Failed to copy portable storage data for player {} during respawn", newPlayer.getName().getString(), e);
            }
        });
    }
}
