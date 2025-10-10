package com.portable.storage.client.ui;

import net.minecraft.item.ItemStack;

public final class VirtualCraftingOverlayState {
    private VirtualCraftingOverlayState() {}

    private static ItemStack[] slots = new ItemStack[10];
    static {
        for (int i = 0; i < slots.length; i++) slots[i] = ItemStack.EMPTY;
    }

    public static void update(ItemStack[] latest) {
        if (latest == null) return;
        int n = Math.min(latest.length, slots.length);
        for (int i = 0; i < n; i++) slots[i] = latest[i].copy();
    }

    public static ItemStack get(int index) {
        if (index < 0 || index >= slots.length) return ItemStack.EMPTY;
        return slots[index];
    }
}


