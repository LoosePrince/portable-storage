package com.portable.storage.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.portable.storage.PortableStorage;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.UpgradeInventory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class XpMaintenanceHandler {
    private static final Map<UUID, Integer> lastPlayerLevels = new HashMap<>();
    
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                checkAndMaintainLevel(player);
            }
        });
    }
    
    private static void checkAndMaintainLevel(ServerPlayerEntity player) {
        UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
        
        // 检查是否有附魔之瓶升级且启用等级维持
        if (!upgrades.isXpBottleUpgradeActive() || !upgrades.isLevelMaintenanceEnabled()) {
            lastPlayerLevels.remove(player.getUuid());
            return;
        }
        
        int currentLevel = player.experienceLevel;
        UUID playerId = player.getUuid();
        
        // 获取上次记录的等级
        Integer lastLevel = lastPlayerLevels.get(playerId);
        if (lastLevel == null) {
            // 首次记录
            lastPlayerLevels.put(playerId, currentLevel);
            return;
        }
        
        // 如果等级没有变化，不需要处理
        if (lastLevel.equals(currentLevel)) {
            return;
        }
        
        // 等级发生变化，执行智能维持
        int levelDiff = currentLevel - lastLevel;
        
        if (levelDiff > 0) {
            // 等级上升，将多余的经验存入仓库
            int xpToDeposit = calculateXpForLevels(lastLevel, levelDiff);
            if (xpToDeposit > 0) {
                upgrades.addToXpPool(xpToDeposit);
                // 将玩家等级恢复到目标等级
                player.experienceLevel = lastLevel;
                player.experienceProgress = 0.0f;
                player.addExperience(0); // 刷新客户端显示
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.maintenance_deposit", xpToDeposit), true);
            }
        } else if (levelDiff < 0) {
            // 等级下降，从仓库取出经验补充
            int xpNeeded = calculateXpForLevels(currentLevel, -levelDiff);
            long availableXp = upgrades.getXpPool();
            
            if (availableXp >= xpNeeded) {
                // 经验足够，完全恢复等级
                upgrades.removeFromXpPool(xpNeeded);
                player.experienceLevel = lastLevel;
                player.experienceProgress = 0.0f;
                player.addExperience(0); // 刷新客户端显示
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.maintenance_withdraw", xpNeeded), true);
            } else if (availableXp > 0) {
                // 经验不足，取出全部剩余经验
                upgrades.removeFromXpPool(availableXp);
                player.addExperience((int)Math.min(Integer.MAX_VALUE, availableXp));
                player.sendMessage(Text.translatable(PortableStorage.MOD_ID + ".exp_bottle.maintenance_partial", availableXp), true);
            }
        }
        
        // 更新记录的等级
        lastPlayerLevels.put(playerId, player.experienceLevel);
    }
    
    private static int calculateXpForLevels(int baseLevel, int levels) {
        if (levels <= 0) return 0;
        
        int from = totalXpForLevel(baseLevel);
        int to = totalXpForLevel(baseLevel + levels);
        return Math.max(0, to - from);
    }
    
    private static int totalXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }
}
