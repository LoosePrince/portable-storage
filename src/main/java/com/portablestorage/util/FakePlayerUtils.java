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

        // Direct real player classes in Minecraft
        Class<?> clazz = player.getClass();
        if (clazz == ServerPlayer.class || clazz.getSimpleName().equals("LocalPlayer") || clazz.getSimpleName().equals("RemotePlayer")) {
            return false;
        }

        // Check class name for known fake player implementations (e.g. Create Deployer, ComputerCraft, etc.)
        String className = clazz.getName().toLowerCase();
        if (className.contains("fakeplayer") || className.contains("fakeentity") || className.endsWith("fake")) {
            return true;
        }

        return false;
    }
}