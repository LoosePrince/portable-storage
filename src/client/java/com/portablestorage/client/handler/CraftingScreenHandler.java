package com.portablestorage.client.handler;

import com.portablestorage.mixin.accessor.SlotAccessor;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class CraftingScreenHandler {
    private static final ResourceLocation WAREHOUSE_SLOT_TEXTURE = com.portablestorage.PortableStorage.id("textures/gui/slot.png");
    private final EffectRenderingInventoryScreen<InventoryMenu> screen;

    public CraftingScreenHandler(EffectRenderingInventoryScreen<InventoryMenu> screen) {
        this.screen = screen;
    }

    public void init() {
        if (!WarehouseUtils.is3x3Enabled(Minecraft.getInstance().player)) return;

        int[] craftIndices = {1, 2, 46, 3, 4, 47, 48, 49, 50};
        for (int i = 0; i < craftIndices.length; i++) {
            var slot = screen.getMenu().slots.get(craftIndices[i]);
            ((SlotAccessor) slot).setX(WarehouseConstants.CRAFT_3X3_X + (i % 3) * 18);
            ((SlotAccessor) slot).setY(WarehouseConstants.CRAFT_3X3_Y + (i / 3) * 18);
        }
        
        var resultSlot = screen.getMenu().slots.get(0);
        ((SlotAccessor) resultSlot).setX(WarehouseConstants.CRAFT_RESULT_X);
        ((SlotAccessor) resultSlot).setY(WarehouseConstants.CRAFT_RESULT_Y);
    }

    public void renderBg(GuiGraphics graphics, int leftPos, int topPos) {
        if (!WarehouseUtils.is3x3Enabled(Minecraft.getInstance().player)) return;

        int cx = leftPos + WarehouseConstants.CRAFT_3X3_X - 1;
        int cy = topPos + WarehouseConstants.CRAFT_3X3_Y - 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                graphics.blit(WAREHOUSE_SLOT_TEXTURE, cx + col * 18, cy + row * 18, 0, 0, 18, 18, 18, 18);
            }
        }
    }
}

