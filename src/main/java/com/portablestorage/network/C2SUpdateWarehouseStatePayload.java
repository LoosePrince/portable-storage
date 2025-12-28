package com.portablestorage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

public record C2SUpdateWarehouseStatePayload(
    Optional<Integer> scrollDelta,
    Optional<String> searchText,
    Optional<Integer> settingId,
    Optional<Integer> settingValue,
    Optional<Integer> rowsDelta
) implements CustomPacketPayload {
    public static final Type<C2SUpdateWarehouseStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "update_state"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpdateWarehouseStatePayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeOptional(payload.scrollDelta(), FriendlyByteBuf::writeInt);
            buf.writeOptional(payload.searchText(), FriendlyByteBuf::writeUtf);
            buf.writeOptional(payload.settingId(), FriendlyByteBuf::writeInt);
            buf.writeOptional(payload.settingValue(), FriendlyByteBuf::writeInt);
            buf.writeOptional(payload.rowsDelta(), FriendlyByteBuf::writeInt);
        },
        buf -> new C2SUpdateWarehouseStatePayload(
            buf.readOptional(FriendlyByteBuf::readInt),
            buf.readOptional(buf2 -> buf2.readUtf(32767)),
            buf.readOptional(FriendlyByteBuf::readInt),
            buf.readOptional(FriendlyByteBuf::readInt),
            buf.readOptional(FriendlyByteBuf::readInt)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

