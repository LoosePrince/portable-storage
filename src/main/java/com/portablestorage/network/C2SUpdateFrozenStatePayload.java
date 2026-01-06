package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SUpdateFrozenStatePayload(boolean frozen) implements CustomPacketPayload {
    public static final Type<C2SUpdateFrozenStatePayload> TYPE = new Type<>(PortableStorage.id("update_frozen_state"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpdateFrozenStatePayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.frozen),
        buf -> new C2SUpdateFrozenStatePayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

