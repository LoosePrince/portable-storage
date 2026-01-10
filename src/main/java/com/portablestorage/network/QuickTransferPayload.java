package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record QuickTransferPayload(int slotId) implements FabricPacket {
    public static final PacketType<QuickTransferPayload> TYPE = PacketType.create(
        PortableStorage.id("quick_transfer"), QuickTransferPayload::read
    );

    public QuickTransferPayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private static QuickTransferPayload read(FriendlyByteBuf buf) {
        return new QuickTransferPayload(buf);
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

