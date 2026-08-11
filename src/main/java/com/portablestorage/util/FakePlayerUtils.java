package com.portablestorage.util;

import net.minecraft.world.entity.player.Player;

public final class FakePlayerUtils {
    private FakePlayerUtils() {
    }

    /**
     * Checks if a player instance is a Fake Player.
     */
    public static boolean isFakePlayer(Player player) {
    if (player == null) {
        return false;
    }

    // Convert class name to lowercase to catch any casing variation
    String className = player.getClass().getName().toLowerCase();
    if (className.contains("fakeplayer") || className.contains("fakeentity") || className.endsWith("fake")) {
        return true;
    }

    // Null connection check on server
    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
        if (serverPlayer.connection == null && !serverPlayer.level().isClientSide()) {
            return true;
        }
    }

    return false;
}
}