package com.portable.storage.net.payload;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class OverlayCraftingSyncS2CPayload {
    public static final Identifier ID = new Identifier("portable-storage", "overlay_crafting_sync");

    private final ItemStack[] slots;

    public OverlayCraftingSyncS2CPayload(ItemStack[] slots) {
        this.slots = slots;
    }

    public ItemStack[] slots() { return slots; }

    public static void write(PacketByteBuf buf, OverlayCraftingSyncS2CPayload value) {
        buf.writeVarInt(value.slots.length);
        for (ItemStack s : value.slots) {
            buf.writeItemStack(s == null ? ItemStack.EMPTY : s);
        }
    }

    public static OverlayCraftingSyncS2CPayload read(PacketByteBuf buf) {
        int n = buf.readVarInt();
        ItemStack[] arr = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            arr[i] = buf.readItemStack();
        }
        return new OverlayCraftingSyncS2CPayload(arr);
    }
}

