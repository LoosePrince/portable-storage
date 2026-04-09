package com.portablestorage.storage.pipeline;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface WarehouseBeforeInsertRule {
    InsertDecision beforeInsert(PlayerWarehouse warehouse, ItemStack stack, long amount, Player player, String source);
}
