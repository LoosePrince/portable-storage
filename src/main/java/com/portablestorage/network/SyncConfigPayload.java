package com.portablestorage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncConfigPayload(boolean enable3x3Crafting) implements CustomPacketPayload {
    public static final Type<SyncConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "sync_config"));
    public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.enable3x3Crafting),
        buf -> new SyncConfigPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

