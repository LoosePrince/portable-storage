package com.portablestorage.handler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;

class WarehouseMenuHandlerRangeImpactTest {
    @Test
    void currentRangeIncludesAnInterleavedNonInventorySlot() {
        Inventory playerInventory = new Inventory(null, null);
        Container insertedSlotContainer = new SimpleContainer(1);
        List<Slot> slots = List.of(
                new Slot(playerInventory, 0, 0, 0),
                new Slot(insertedSlotContainer, 0, 0, 0),
                new Slot(playerInventory, 1, 0, 0));

        assertArrayEquals(new int[] { 0, 3 }, WarehouseMenuHandler.findPlayerInventoryRange(slots));
        assertFalse(slots.get(1).container instanceof Inventory);
    }
}