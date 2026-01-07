package com.portablestorage.client.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Unique;

/**
 * Utility to easily attach a WarehouseWidget to any AbstractContainerScreen.
 */
public class WarehouseBridge {
    
    public static WarehouseWidget attach(AbstractContainerScreen<?> screen) {
        return new WarehouseWidget(screen);
    }
}

