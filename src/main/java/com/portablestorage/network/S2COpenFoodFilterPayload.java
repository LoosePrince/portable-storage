package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record S2COpenFoodFilterPayload(List<String> filters, boolean blacklist) implements CustomPacketPayload {
    public static final Type<S2COpenFoodFilterPayload> TYPE = new Type<>(PortableStorage.id("open_food_filter"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenFoodFilterPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeCollection(payload.filters, FriendlyByteBuf::writeUtf);
            buf.writeBoolean(payload.blacklist);
        },
        buf -> new S2COpenFoodFilterPayload(buf.readList(FriendlyByteBuf::readUtf), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
