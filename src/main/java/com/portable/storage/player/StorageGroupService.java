package com.portable.storage.player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 基于“绑定木桶所有者”聚合共享视图并从中提取物品的服务。
 */
public final class StorageGroupService {
    private StorageGroupService() {}

    /**
     * 根据绑定木桶所有者，构建其共享仓库视图（包含：所有者本人的仓库 + 使用其绑定木桶的在线玩家的仓库 + 所有者离线时从磁盘加载）。
     */
    public static List<StorageInventory> getStoragesByOwner(MinecraftServer server, UUID ownerUuid) {
        List<StorageInventory> list = new ArrayList<>();
        LinkedHashSet<UUID> added = new LinkedHashSet<>();

        // 所有者：在线优先，否则离线加载
        ServerPlayerEntity ownerOnline = server.getPlayerManager().getPlayer(ownerUuid);
        if (ownerOnline != null) {
            if (added.add(ownerUuid)) list.add(PlayerStorageService.getInventory(ownerOnline));
        } else {
            if (added.add(ownerUuid)) list.add(StoragePersistence.loadStorage(server, ownerUuid));
        }

        // 所有在线玩家中，凡是其升级槽位包含“绑定到 ownerUuid 的木桶”，均加入其仓库
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UpgradeInventory up = PlayerStorageService.getUpgradeInventory(p);
            boolean usesOwner = false;
            for (int i = 0; i < up.getSlotCount(); i++) {
                ItemStack st = up.getStack(i);
                if (!st.isEmpty() && st.getItem() == net.minecraft.item.Items.BARREL) {
                    UUID u = tryGetOwnerUuidFromItem(st);
                    if (u != null && u.equals(ownerUuid)) { usesOwner = true; break; }
                }
            }
            if (usesOwner && added.add(p.getUuid())) {
                list.add(PlayerStorageService.getInventory(p));
            }
        }

        return list;
    }

    /**
     * 从“owner 的共享视图”中按物品变体提取指定数量。
     * 返回实际提取出的物品堆（数量<=want，保留组件）。
     */
    public static ItemStack takeFromOwnerGroup(MinecraftServer server, UUID ownerUuid, ItemStack variant, int want) {
        if (variant == null || variant.isEmpty() || want <= 0) return ItemStack.EMPTY;
        List<StorageInventory> view = getStoragesByOwner(server, ownerUuid);
        long remaining = want;
        long got = 0;
        long ts = System.currentTimeMillis();

        for (StorageInventory s : view) {
            if (remaining <= 0) break;
            for (int i = 0; i < s.getCapacity() && remaining > 0; i++) {
                ItemStack disp = s.getDisplayStack(i);
                if (disp.isEmpty()) continue;
                if (ItemStack.areItemsAndComponentsEqual(disp, variant)) {
                    long can = Math.min(remaining, s.getCountByIndex(i));
                    if (can > 0) {
                        long t = s.takeByIndex(i, (int)can, ts);
                        got += t;
                        remaining -= t;
                    }
                }
            }
        }

        if (got <= 0) return ItemStack.EMPTY;
        ItemStack out = variant.copy();
        out.setCount((int)Math.min(variant.getMaxCount(), got));

        // 发送同步消息给所有受影响的玩家
        sendSyncToAffectedPlayers(server, ownerUuid);

        // 如果所有者离线，持久化修改（已加载的离线仓库对象发生了改变）
        ServerPlayerEntity ownerOnline = server.getPlayerManager().getPlayer(ownerUuid);
        if (ownerOnline == null) {
            // 仅保存所有者；其余在线玩家由游戏生命周期保存
            List<StorageInventory> byOwner = getStoragesByOwner(server, ownerUuid);
            if (!byOwner.isEmpty()) {
                StoragePersistence.saveStorage(server, ownerUuid, byOwner.get(0));
            }
        }

        return out;
    }

    /**
     * 发送同步消息给所有受ownerUuid影响的玩家
     */
    private static void sendSyncToAffectedPlayers(MinecraftServer server, UUID ownerUuid) {
        var players = server.getPlayerManager().getPlayerList();

        // 发送给所有者
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) {
            ServerNetworkingHandlers.sendSync(owner);
        }

        // 发送给所有使用该ownerUuid绑定木桶的玩家
        for (ServerPlayerEntity player : players) {
            if (player.getUuid().equals(ownerUuid)) continue; // 所有者已经处理过了

            UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);
            boolean usesThisOwner = false;

            for (int i = 0; i < upgrades.getSlotCount(); i++) {
                ItemStack stack = upgrades.getStack(i);
                if (!stack.isEmpty() && stack.getItem() == net.minecraft.item.Items.BARREL) {
                    UUID barrelOwner = tryGetOwnerUuidFromItem(stack);
                    if (barrelOwner != null && barrelOwner.equals(ownerUuid)) {
                        usesThisOwner = true;
                        break;
                    }
                }
            }

            if (usesThisOwner) {
                ServerNetworkingHandlers.sendSync(player);
            }
        }
    }

    private static java.util.UUID tryGetOwnerUuidFromItem(net.minecraft.item.ItemStack stack) {
        try {
            net.minecraft.component.type.NbtComponent comp = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            if (comp == null) return null;
            net.minecraft.nbt.NbtCompound nbt = comp.copyNbt();
            if (nbt.contains("ps_owner_uuid_most") && nbt.contains("ps_owner_uuid_least")) {
                return new java.util.UUID(nbt.getLong("ps_owner_uuid_most"), nbt.getLong("ps_owner_uuid_least"));
            }
            if (nbt.contains("ps_owner_uuid")) {
                return nbt.getUuid("ps_owner_uuid");
            }
        } catch (Throwable ignored) {}
        return null;
    }
}


