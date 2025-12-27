package com.portablestorage.mixin.client;

import com.portablestorage.config.ModConfig;
import com.portablestorage.mixin.accessor.SlotAccessor;
import com.portablestorage.util.WarehouseConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenCraftingMixin extends net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen<InventoryMenu> {

    @Unique
    private static final ResourceLocation WAREHOUSE_SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath("portablestorage", "textures/gui/slot.png");

    public InventoryScreenCraftingMixin(InventoryMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.network.chat.Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    protected void onInitCrafting(CallbackInfo ci) {
        if (!ModConfig.is3x3Enabled()) return;

        int[] craftIndices = {1, 2, 3, 4, 46, 47, 48, 49, 50};
        for (int i = 0; i < craftIndices.length; i++) {
            var slot = this.getMenu().slots.get(craftIndices[i]);
            ((SlotAccessor) slot).setX(WarehouseConstants.CRAFT_3X3_X + (i % 3) * 18);
            ((SlotAccessor) slot).setY(WarehouseConstants.CRAFT_3X3_Y + (i / 3) * 18);
        }
        
        var resultSlot = this.getMenu().slots.get(0);
        ((SlotAccessor) resultSlot).setX(WarehouseConstants.CRAFT_RESULT_X);
        ((SlotAccessor) resultSlot).setY(WarehouseConstants.CRAFT_RESULT_Y);
    }

    @Inject(method = "renderBg", at = @At("RETURN"))
    protected void onRenderBgCrafting(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ModConfig.is3x3Enabled()) return;

        int cx = this.leftPos + WarehouseConstants.CRAFT_3X3_X - 1;
        int cy = this.topPos + WarehouseConstants.CRAFT_3X3_Y - 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                graphics.blit(WAREHOUSE_SLOT_TEXTURE, cx + col * 18, cy + row * 18, 0, 0, 18, 18, 18, 18);
            }
        }
    }
}

