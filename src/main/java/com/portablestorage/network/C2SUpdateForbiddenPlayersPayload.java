package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

import java.util.UUID;

public record C2SUpdateForbiddenPlayersPayload(UUID playerUuid, boolean forbidden) implements FabricPacket {
    public static final PacketType<C2SUpdateForbiddenPlayersPayload> TYPE = PacketType.create(
        PortableStorage.id("update_forbidden_players"), C2SUpdateForbiddenPlayersPayload::read
    );

    public C2SUpdateForbiddenPlayersPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readBoolean());
    }

    private static C2SUpdateForbiddenPlayersPayload read(FriendlyByteBuf buf) {
        return new C2SUpdateForbiddenPlayersPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(playerUuid);
        buf.writeBoolean(forbidden);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

