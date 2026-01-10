package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record C2STogglePinnedPayload(int slotId) implements FabricPacket {
    public static final PacketType<C2STogglePinnedPayload> TYPE = PacketType.create(
        PortableStorage.id("toggle_pinned"), C2STogglePinnedPayload::read
    );

    public C2STogglePinnedPayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private static C2STogglePinnedPayload read(FriendlyByteBuf buf) {
        return new C2STogglePinnedPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(slotId);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

