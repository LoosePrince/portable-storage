package com.portablestorage.mixin.client;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenCraftingMixin extends net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen<InventoryMenu> {
    public InventoryScreenCraftingMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.network.chat.Component title) {
        super(menu, playerInventory, title);
    }
    // Logic moved to WarehouseWidget called from InventoryScreenMixin
}
