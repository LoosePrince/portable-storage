package com.portablestorage.handler;

import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseUtils;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class InventoryMenuHandler {

    public static void addExtraCraftingSlots(AbstractContainerMenu menu, CraftingContainer craftSlots, Player owner) {
        int[] extraIndices = {2, 5, 6, 7, 8};
        
        int[][] positions = {
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y},
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 18},
            {WarehouseConstants.CRAFT_3X3_X, WarehouseConstants.CRAFT_3X3_Y + 2 * 18},
            {WarehouseConstants.CRAFT_3X3_X + 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18},
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18}
        };

        for (int i = 0; i < extraIndices.length; i++) {
            final int idx = extraIndices[i];
            ((AbstractContainerMenuAccessor) menu).invokeAddSlot(new Slot(craftSlots, idx, positions[i][0], positions[i][1]) {
                @Override
                public boolean isActive() {
                    return WarehouseUtils.is3x3Enabled(owner);
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return WarehouseUtils.is3x3Enabled(owner);
                }
            });
        }
    }

    public static ItemStack handleCraftingQuickMove(AbstractContainerMenu menu, List<Slot> slots, CraftingContainer craftSlots, Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return null;

        if (WarehouseUtils.is3x3Enabled(player)) {
            if (slot instanceof ResultSlot || slot.container == craftSlots) {
                ItemStack stackInSlot = slot.getItem();
                ItemStack resultStack = stackInSlot.copy();

                int invStart = -1;
                int invEnd = -1;
                for (int i = 0; i < slots.size(); i++) {
                    Slot s = slots.get(i);
                    if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
                        if (invStart == -1) invStart = i;
                        invEnd = i + 1;
                    }
                }

                if (slot instanceof ResultSlot) {
                    if (invStart != -1) {
                        while (slot.hasItem()) {
                            ItemStack currentResult = slot.getItem();
                            ItemStack resultCopy = currentResult.copy();
                            currentResult.getItem().onCraftedBy(currentResult, player.level(), player);
                            if (!((AbstractContainerMenuAccessor) menu).invokeMoveItemStackTo(currentResult, invStart, invEnd, true)) {
                                break;
                            }
                            slot.onQuickCraft(currentResult, resultCopy);
                            slot.onTake(player, currentResult);
                            if (currentResult.getCount() == resultCopy.getCount()) {
                                break;
                            }
                        }
                    }
                } else {
                    if (invStart != -1) {
                        if (!((AbstractContainerMenuAccessor) menu).invokeMoveItemStackTo(stackInSlot, invStart, invEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                    slot.onQuickCraft(stackInSlot, resultStack);
                    slot.setChanged();
                    menu.slotsChanged(craftSlots);
                }
                return ItemStack.EMPTY;
            }
        }
        return null;
    }
}

