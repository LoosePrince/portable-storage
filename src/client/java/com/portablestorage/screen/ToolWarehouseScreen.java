package com.portablestorage.screen;

import com.portablestorage.PortableStorage;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class ToolWarehouseScreen extends AbstractContainerScreen<ToolWarehouseScreenHandler> {
    private static final Identifier TEXTURE = PortableStorage.id("textures/gui/gui2.png");
    private static final Identifier SLOT_TEXTURE = PortableStorage.id("textures/gui/slot.png");

    public ToolWarehouseScreen(ToolWarehouseScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 133);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth,
                this.imageHeight, this.imageWidth, this.imageHeight);
        for (int col = 0; col < 9; col++) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, this.leftPos + 7 + col * 18, this.topPos + 19,
                    0, 0, 18, 18, 18, 18, 18, 18);
        }
    }
}
