package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import com.portable.storage.command.NewStoreCommands;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;
import com.portable.storage.sync.ChangeAccumulator;
import com.portable.storage.sync.PlayerViewState;
import com.portable.storage.sync.StorageSyncManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 处理玩家加入和离开游戏的事件
 */
public class PlayerJoinEventHandler {
    private static final java.util.Map<java.util.UUID, Integer> pendingMigrations = new java.util.HashMap<>();
    
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            // 玩家加入时发送启用状态同步
            ServerNetworkingHandlers.sendEnablementSync(player);
            // 1秒后（20tick）尝试迁移旧版到新版
            pendingMigrations.put(player.getUuid(), 20);
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            // 玩家离开时清理同步状态
            StorageSyncManager.cleanupPlayer(player.getUuid());
            // 清理按需同步状态
            PlayerViewState.cleanupPlayer(player.getUuid());
            ChangeAccumulator.cleanupPlayer(player.getUuid());
            // 清理活塞升级处理器数据
            PistonUpgradeHandler.cleanupPlayer(player.getUuid());
            // 清空垃圾桶槽位（客户端缓存）
            UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
            if (upgrades.isTrashSlotActive()) {
                upgrades.setTrashSlot(net.minecraft.item.ItemStack.EMPTY);
            }
            pendingMigrations.remove(player.getUuid());
        });

        // 服务器tick：处理延时迁移
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (pendingMigrations.isEmpty()) return;
            java.util.ArrayList<java.util.UUID> due = new java.util.ArrayList<>();
            for (var e : pendingMigrations.entrySet()) {
                int left = e.getValue() - 1;
                if (left <= 0) due.add(e.getKey());
                else pendingMigrations.put(e.getKey(), left);
            }
            for (java.util.UUID id : due) {
                pendingMigrations.remove(id);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p == null) continue;
                // 检查旧版是否有物品
                StorageInventory legacy = PlayerStorageService.getInventory(p);
                boolean hasOld = false;
                for (int i = 0; i < legacy.getCapacity() && !hasOld; i++) {
                    if (legacy.getCountByIndex(i) > 0) hasOld = true;
                }
                if (!hasOld) continue; // 无旧物品，跳过且不提示
                boolean ok = NewStoreCommands.migrateOne(server, id);
                if (ok) {
                    p.sendMessage(net.minecraft.text.Text.translatable("message." + PortableStorage.MOD_ID + ".newstore.migrated"));
                }
            }
        });
    }
}
