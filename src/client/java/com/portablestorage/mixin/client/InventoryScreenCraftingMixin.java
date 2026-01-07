package com.portablestorage.mixin.client;

import com.portablestorage.client.handler.CraftingScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenCraftingMixin extends net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen<InventoryMenu> {

    @Unique
    private CraftingScreenHandler craftingHandler;

    public InventoryScreenCraftingMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.network.chat.Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        if (this.craftingHandler == null) {
            this.craftingHandler = new CraftingScreenHandler(this);
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInitCrafting(CallbackInfo ci) {
        craftingHandler.init();
    }

    @Inject(method = "renderBg", at = @At("RETURN"))
    protected void onRenderBgCrafting(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        craftingHandler.renderBg(graphics, this.leftPos, this.topPos);
    }
}
