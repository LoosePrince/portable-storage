package com.portablestorage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SearchPayload(String searchText) implements CustomPacketPayload {
    public static final Type<SearchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "search"));
    public static final StreamCodec<FriendlyByteBuf, SearchPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.searchText),
        buf -> new SearchPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
