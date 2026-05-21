package com.portablestorage.network;

import com.portablestorage.PortableStorage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SQuickToolSwapPayload(int slot) implements CustomPacketPayload {
    public static final Type<C2SQuickToolSwapPayload> TYPE = new Type<>(PortableStorage.id("quick_tool_swap"));

    public static final StreamCodec<FriendlyByteBuf, C2SQuickToolSwapPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.slot()),
            buf -> new C2SQuickToolSwapPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}