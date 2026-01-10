package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record C2SUpdateFrozenStatePayload(boolean frozen) implements FabricPacket {
    public static final PacketType<C2SUpdateFrozenStatePayload> TYPE = PacketType.create(
        PortableStorage.id("update_frozen_state"), C2SUpdateFrozenStatePayload::read
    );

    public C2SUpdateFrozenStatePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private static C2SUpdateFrozenStatePayload read(FriendlyByteBuf buf) {
        return new C2SUpdateFrozenStatePayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(frozen);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

