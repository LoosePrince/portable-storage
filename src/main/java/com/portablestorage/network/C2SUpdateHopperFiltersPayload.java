package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record C2SUpdateHopperFiltersPayload(List<String> filters) implements CustomPacketPayload {
    public static final Type<C2SUpdateHopperFiltersPayload> TYPE = new Type<>(PortableStorage.id("update_hopper_filters"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpdateHopperFiltersPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeCollection(payload.filters, FriendlyByteBuf::writeUtf);
        },
        buf -> new C2SUpdateHopperFiltersPayload(buf.readList(FriendlyByteBuf::readUtf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

