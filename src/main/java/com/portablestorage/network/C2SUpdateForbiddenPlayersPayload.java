package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record C2SUpdateForbiddenPlayersPayload(UUID playerUuid, boolean forbidden) implements CustomPacketPayload {
    public static final Type<C2SUpdateForbiddenPlayersPayload> TYPE = new Type<>(PortableStorage.id("update_forbidden_players"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpdateForbiddenPlayersPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUUID(payload.playerUuid);
            buf.writeBoolean(payload.forbidden);
        },
        buf -> new C2SUpdateForbiddenPlayersPayload(buf.readUUID(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

