package com.portablestorage.screen;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BoundBarrelScreen extends AbstractContainerScreen<BoundBarrelScreenHandler> {
    public BoundBarrelScreen(BoundBarrelScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 133);
        this.inventoryLabelY = this.imageHeight - 94;
    }
}
