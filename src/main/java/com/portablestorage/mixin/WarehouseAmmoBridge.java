package com.portablestorage.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class WarehouseAmmoBridge {
    private static final Map<AmmoUseKey, ItemStack> PENDING = new HashMap<>();

    private WarehouseAmmoBridge() {
    }

    static void remember(ServerPlayer player, ItemStack weapon, ItemStack ammo) {
        if (player == null || weapon.isEmpty() || ammo.isEmpty()) {
            return;
        }
        PENDING.put(AmmoUseKey.of(player, weapon), ammo.copyWithCount(1));
    }

    static ItemStack consume(ServerPlayer player, ItemStack weapon) {
        if (player == null || weapon.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack ammo = PENDING.remove(AmmoUseKey.of(player, weapon));
        return ammo == null ? ItemStack.EMPTY : ammo;
    }

    static void clear(ServerPlayer player, ItemStack weapon) {
        if (player == null || weapon.isEmpty()) {
            return;
        }
        PENDING.remove(AmmoUseKey.of(player, weapon));
    }

    private record AmmoUseKey(UUID playerUuid, int weaponIdentity) {
        private static AmmoUseKey of(ServerPlayer player, ItemStack weapon) {
            return new AmmoUseKey(player.getUUID(), System.identityHashCode(weapon));
        }
    }
}