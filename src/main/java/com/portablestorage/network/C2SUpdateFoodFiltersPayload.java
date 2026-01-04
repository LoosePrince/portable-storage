package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record C2SUpdateFoodFiltersPayload(List<String> filters) implements CustomPacketPayload {
    public static final Type<C2SUpdateFoodFiltersPayload> TYPE = new Type<>(PortableStorage.id("update_food_filters"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpdateFoodFiltersPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeCollection(payload.filters, FriendlyByteBuf::writeUtf);
        },
        buf -> new C2SUpdateFoodFiltersPayload(buf.readList(FriendlyByteBuf::readUtf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
