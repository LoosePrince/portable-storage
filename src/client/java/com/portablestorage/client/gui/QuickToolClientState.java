package com.portablestorage.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.portablestorage.PortableStorageClient;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.C2SQuickToolSwapPayload;
import com.portablestorage.storage.sync.ClientWarehouseState;
import com.portablestorage.upgrade.ToolUpgrade;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class QuickToolClientState {
    private static final Identifier SLOT_TEXTURE = com.portablestorage.PortableStorage.id("textures/gui/slot.png");
    private static final int SLOT_SIZE = 18;
    private static final int TOOL_SLOT_COUNT = 9;
    private static final int DISPLAY_SLOT_COUNT = TOOL_SLOT_COUNT + 1;
    private static final int EMPTY_SELECTION = -1;
    private static boolean selecting;
    private static int selectedSlot;

    private QuickToolClientState() {
    }

    public static boolean isSelecting() {
        return selecting;
    }

    public static void tick(Minecraft client) {
        if (client.player == null || PortableStorageClient.quickToolKey == null) {
            selecting = false;
            selectedSlot = EMPTY_SELECTION;
            return;
        }

        boolean pressed = PortableStorageClient.quickToolKey.isDown();
        if (pressed && !selecting) {
            selecting = true;
            selectedSlot = EMPTY_SELECTION;
        } else if (!pressed && selecting) {
            selecting = false;
            submitSelectedSlot(client);
        }
    }

    public static boolean handleNumberKey(int keyCode) {
        return handleKeyPress(keyCode, 1);
    }

    public static boolean handleKeyPress(int keyCode, int action) {
        if (!selecting || action != 1) {
            return false;
        }
        int slot = switch (keyCode) {
            case InputConstants.KEY_0 -> EMPTY_SELECTION;
            case InputConstants.KEY_1 -> 0;
            case InputConstants.KEY_2 -> 1;
            case InputConstants.KEY_3 -> 2;
            case InputConstants.KEY_4 -> 3;
            case InputConstants.KEY_5 -> 4;
            case InputConstants.KEY_6 -> 5;
            case InputConstants.KEY_7 -> 6;
            case InputConstants.KEY_8 -> 7;
            case InputConstants.KEY_9 -> 8;
            default -> -2;
        };
        if (slot < EMPTY_SELECTION) {
            return false;
        }
        selectedSlot = slot;
        return true;
    }

    public static boolean handleScroll(double scrollY) {
        if (!selecting) {
            return false;
        }
        int delta = (int) Math.signum(scrollY);
        if (delta == 0) {
            return true;
        }
        selectedSlot = displayIndexToToolSlot(Math.floorMod(toolSlotToDisplayIndex(selectedSlot) - delta, DISPLAY_SLOT_COUNT));
        return true;
    }

    private static int toolSlotToDisplayIndex(int toolSlot) {
        return toolSlot == EMPTY_SELECTION ? 0 : toolSlot + 1;
    }

    private static int displayIndexToToolSlot(int displayIndex) {
        return displayIndex == 0 ? EMPTY_SELECTION : displayIndex - 1;
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client) {
        if (!selecting || client.player == null || ClientScreens.current(client) != null) {
            return;
        }
        PlayerWarehouse warehouse = ClientWarehouseState.current();
        if (warehouse == null || !warehouse.isEnabled() || warehouse.getUpgrade(ToolUpgrade.ID).isEmpty()) {
            return;
        }

        float scale = 0.8f;
        int totalWidth = DISPLAY_SLOT_COUNT * SLOT_SIZE;
        int scaledWidth = Math.round(totalWidth * scale);
        int x = (client.getWindow().getGuiScaledWidth() - scaledWidth) / 2;
        int y = client.getWindow().getGuiScaledHeight() - 56;

        ItemStack selectedStack = selectedSlot == EMPTY_SELECTION ? ItemStack.EMPTY : warehouse.getToolSlotStack(selectedSlot);
        if (selectedSlot == EMPTY_SELECTION) {
            int nameY = y - client.font.lineHeight - 4;
            graphics.centeredText(client.font, net.minecraft.network.chat.Component.translatable("gui.portablestorage.quick_tool.cancel"),
                    client.getWindow().getGuiScaledWidth() / 2, nameY, 0xFFFFFFFF);
        } else if (!selectedStack.isEmpty()) {
            int nameY = y - client.font.lineHeight - 4;
            graphics.centeredText(client.font, selectedStack.getHoverName(), client.getWindow().getGuiScaledWidth() / 2,
                    nameY, 0xFFFFFFFF);
        }

        int selectedDisplayIndex = toolSlotToDisplayIndex(selectedSlot);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        for (int i = 0; i < DISPLAY_SLOT_COUNT; i++) {
            int slotX = i * SLOT_SIZE;
            if (i > 0) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, slotX, 0, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE,
                        SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
                ItemStack stack = warehouse.getToolSlotStack(i - 1);
                if (!stack.isEmpty()) {
                    graphics.fakeItem(stack, slotX + 1, 1);
                    graphics.itemDecorations(client.font, stack, slotX + 1, 1);
                }
            }
            if (i == selectedDisplayIndex) {
                graphics.fill(slotX, 0, slotX + SLOT_SIZE, 1, 0xFFFFFFFF);
                graphics.fill(slotX, SLOT_SIZE - 1, slotX + SLOT_SIZE, SLOT_SIZE, 0xFFFFFFFF);
                graphics.fill(slotX, 0, slotX + 1, SLOT_SIZE, 0xFFFFFFFF);
                graphics.fill(slotX + SLOT_SIZE - 1, 0, slotX + SLOT_SIZE, SLOT_SIZE, 0xFFFFFFFF);
            }
        }
        graphics.pose().popMatrix();
    }

    private static void submitSelectedSlot(Minecraft client) {
        if (client.player == null) {
            return;
        }
        PlayerWarehouse warehouse = ClientWarehouseState.current();
        if (warehouse == null || !warehouse.isEnabled() || warehouse.getUpgrade(ToolUpgrade.ID).isEmpty()) {
            return;
        }
        if (selectedSlot == EMPTY_SELECTION) {
            return;
        }
        ClientPlayNetworking.send(new C2SQuickToolSwapPayload(selectedSlot));
    }
}