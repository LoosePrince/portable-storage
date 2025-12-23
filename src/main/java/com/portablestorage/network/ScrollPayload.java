package com.portablestorage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScrollPayload(int delta) implements CustomPacketPayload {
    public static final Type<ScrollPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "scroll"));
    
    public static final StreamCodec<FriendlyByteBuf, ScrollPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.delta),
        buf -> new ScrollPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

