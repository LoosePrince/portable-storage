package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2STogglePinnedPayload(int slotId) implements CustomPacketPayload {
    public static final Type<C2STogglePinnedPayload> TYPE = new Type<>(PortableStorage.id("toggle_pinned"));

    public static final StreamCodec<FriendlyByteBuf, C2STogglePinnedPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.slotId),
        buf -> new C2STogglePinnedPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

