package com.portablestorage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChangeRowsPayload(int delta) implements CustomPacketPayload {
    public static final Type<ChangeRowsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "change_rows"));
    
    public static final StreamCodec<FriendlyByteBuf, ChangeRowsPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.delta),
        buf -> new ChangeRowsPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

