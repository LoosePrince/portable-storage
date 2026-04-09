package com.portablestorage.storage.pipeline;

import java.util.ArrayList;
import java.util.List;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.StorageWriteAudit;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WarehouseWritePipeline {
    private static final List<WarehouseBeforeInsertRule> RULES = new ArrayList<>();

    static {
        RULES.add((warehouse, stack, amount, player, source) -> {
            if (!warehouse.isEnabled()) {
                return InsertDecision.deny("warehouse_disabled");
            }
            if (amount <= 0 || stack.isEmpty()) {
                return InsertDecision.deny("empty_input");
            }
            return InsertDecision.allow(amount);
        });
    }

    private WarehouseWritePipeline() {
    }

    public static InsertDecision beforeInsert(PlayerWarehouse warehouse, ItemStack stack, long amount, Player player,
            String source) {
        InsertDecision decision = InsertDecision.allow(amount);
        for (WarehouseBeforeInsertRule rule : RULES) {
            decision = rule.beforeInsert(warehouse, stack, decision.amount(), player, source);
            if (!decision.allowed()) {
                StorageWriteAudit.record(source, "reject:" + decision.reason());
                return decision;
            }
        }
        StorageWriteAudit.record(source, "allow");
        return decision;
    }

    public static void registerRule(WarehouseBeforeInsertRule rule) {
        RULES.add(rule);
    }
}
