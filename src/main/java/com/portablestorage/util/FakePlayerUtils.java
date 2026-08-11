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

        String className = player.getClass().getName();
        // 1. Class name checks (FakePlayer mod, Carpet, FakePlayerEntity, etc.)
        if (className.contains("fakeplayer")
                || className.contains("FakePlayer")
                || className.contains("FakeEntity")
                || className.endsWith("Fake")) {
            return true;
        }

        // 2. Null connection check on server
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (serverPlayer.connection == null && !serverPlayer.level().isClientSide()) {
                return true;
            }
        }

        return false;
    }
}