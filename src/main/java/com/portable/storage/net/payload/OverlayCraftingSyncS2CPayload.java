package com.portable.storage.net.payload;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OverlayCraftingSyncS2CPayload(ItemStack[] slots) implements CustomPayload {
    public static final Id<OverlayCraftingSyncS2CPayload> ID = new Id<>(Identifier.of("portable-storage", "overlay_crafting_sync"));
    public static final PacketCodec<RegistryByteBuf, OverlayCraftingSyncS2CPayload> PACKET_CODEC = PacketCodec.of((value, buf) -> {
        buf.writeVarInt(value.slots.length);
        for (ItemStack s : value.slots) {
            boolean present = s != null && !s.isEmpty();
            buf.writeBoolean(present);
            if (present) ItemStack.PACKET_CODEC.encode(buf, s);
        }
    }, buf -> {
        int n = buf.readVarInt();
        ItemStack[] arr = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            boolean present = buf.readBoolean();
            arr[i] = present ? ItemStack.PACKET_CODEC.decode(buf) : ItemStack.EMPTY;
        }
        return new OverlayCraftingSyncS2CPayload(arr);
    });

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

 
