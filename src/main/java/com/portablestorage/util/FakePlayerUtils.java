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

        // On the server, real human players are always ServerPlayer.class directly.
        // Fake players from mods (Create, ComputerCraft, etc.) are always subclasses
        if (player instanceof ServerPlayer) {
            return player.getClass() != ServerPlayer.class;
        }

        // On the client, check class name as fallback
        String className = player.getClass().getName().toLowerCase();
        return className.contains("fakeplayer") || className.contains("fakeentity") || className.endsWith("fake");
    }
}