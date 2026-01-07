package com.portablestorage.mixin.client;

import com.portablestorage.config.ModConfig;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public abstract class CraftingScreenMixin extends AbstractContainerScreen<CraftingMenu> {

    public CraftingScreenMixin(CraftingMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
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
    }
}
