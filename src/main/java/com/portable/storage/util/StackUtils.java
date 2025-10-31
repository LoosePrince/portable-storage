package com.portable.storage.util;

import net.minecraft.item.ItemStack;

public final class StackUtils {
    private StackUtils() {}

    public static boolean areItemsAndComponentsEqual(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        // Compare NBT when items match
        net.minecraft.nbt.NbtCompound an = a.getNbt();
        net.minecraft.nbt.NbtCompound bn = b.getNbt();
        if (an == null || an.isEmpty()) return bn == null || bn.isEmpty();
        return an.equals(bn);
    }
}


