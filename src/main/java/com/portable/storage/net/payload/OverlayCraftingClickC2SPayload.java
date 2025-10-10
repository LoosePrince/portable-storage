package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OverlayCraftingClickC2SPayload(int slotIndex, int button, boolean shift) implements CustomPayload {
    public static final Id<OverlayCraftingClickC2SPayload> ID = new Id<>(Identifier.of("portable-storage", "overlay_crafting_click"));
    public static final PacketCodec<RegistryByteBuf, OverlayCraftingClickC2SPayload> PACKET_CODEC = PacketCodec.of((value, buf) -> {
        buf.writeVarInt(value.slotIndex);
        buf.writeVarInt(value.button);
        buf.writeBoolean(value.shift);
    }, buf -> new OverlayCraftingClickC2SPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

 