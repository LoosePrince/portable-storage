package com.portablestorage.mixin.client;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenCraftingMixin extends AbstractRecipeBookScreen<InventoryMenu> {
    public InventoryScreenCraftingMixin(InventoryMenu menu,
            RecipeBookComponent<InventoryMenu> recipeBook,
            Inventory playerInventory,
            Component title) {
        super(menu, recipeBook, playerInventory, title);
    }
    // Logic moved to WarehouseWidget called from InventoryScreenMixin
}
