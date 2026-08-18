package com.portablestorage.util;

import net.minecraft.server.level.ServerPlayer;
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

        if (isSubclassOf(player.getClass(), ServerPlayer.class)) {
            CompatibilityDebug.logOnce("fake-player:" + player.getClass().getName(), "fake-player",
                    () -> "classified server player subclass as fake: " + player.getClass().getName());
            return true;
        }

        String className = player.getClass().getName().toLowerCase();
        boolean fakeByName = className.contains("fakeplayer") || className.contains("fakeentity")
                || className.endsWith("fake");
        if (fakeByName) {
            CompatibilityDebug.logOnce("fake-player:" + player.getClass().getName(), "fake-player",
                    () -> "classified client-side player by class name as fake: " + player.getClass().getName());
        }
        return fakeByName;
    }

    static boolean isSubclassOf(Class<?> candidate, Class<?> base) {
        return candidate != null && base != null && base.isAssignableFrom(candidate) && candidate != base;
    }
}