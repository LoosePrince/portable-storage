package com.portablestorage.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.config.ModConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {

    public InventoryScreenMixin(InventoryMenu menu,
            RecipeBookComponent<InventoryMenu> recipeBook,
            Inventory playerInventory,
            Component title) {
        super(menu, recipeBook, playerInventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void hideRecipeBookButton(CallbackInfo ci) {
        if (ModConfig.hideRecipeBook) {
            this.children().removeIf(child -> {
                if (child instanceof AbstractWidget btn) {
                    if (btn.getWidth() == 20 && (btn.getHeight() == 18 || btn.getHeight() == 19)) {
                        return true;
                    }
                }
                return false;
            });
            ((ScreenAccessor) this).portablestorage$getRenderables().removeIf(renderable -> {
                if (renderable instanceof AbstractWidget btn) {
                    if (btn.getWidth() == 20 && (btn.getHeight() == 18 || btn.getHeight() == 19)) {
                        return true;
                    }
                }
                return false;
            });
        }

        // 重新应用偏移，因为 InventoryScreen.init() 会覆盖 leftPos
        WarehouseScreen screen = (WarehouseScreen) this;
        var warehouseWidget = screen.portablestorage$getWarehouseWidget();
        if (warehouseWidget != null) {
            warehouseWidget.refreshPosition();
        }
    }

    @Inject(method = "extractBackground", at = @At("RETURN"))
    private void onExtractBackgroundReturn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        WarehouseScreen screen = (WarehouseScreen) this;
        var warehouseWidget = screen.portablestorage$getWarehouseWidget();
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderBackground(graphics, mouseX, mouseY);
        }
    }

}
