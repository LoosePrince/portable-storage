package com.portablestorage.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientScreens {
    private ClientScreens() {
    }

    public static Screen current(Minecraft client) {
        return client.gui.screen();
    }

    public static void show(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }
}