package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record S2COpenHopperFilterPayload(List<String> filters) implements CustomPacketPayload {
    public static final Type<S2COpenHopperFilterPayload> TYPE = new Type<>(PortableStorage.id("open_hopper_filter"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenHopperFilterPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeCollection(payload.filters, FriendlyByteBuf::writeUtf);
        },
        buf -> new S2COpenHopperFilterPayload(buf.readList(FriendlyByteBuf::readUtf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

