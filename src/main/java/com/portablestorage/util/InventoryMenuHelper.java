package com.portablestorage.util;

import net.minecraft.world.entity.player.Player;

public final class InventoryMenuHelper {
    public static final ThreadLocal<Player> CURRENT_INVENTORY_OWNER = new ThreadLocal<>();

    private InventoryMenuHelper() {
    }
}