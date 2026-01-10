package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

public record C2SDropWarehouseItemPayload(int slotId, boolean dropFullStack) implements FabricPacket {
    public static final PacketType<C2SDropWarehouseItemPayload> TYPE = PacketType.create(
        PortableStorage.id("drop_warehouse_item"), C2SDropWarehouseItemPayload::read
    );

    public C2SDropWarehouseItemPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    private static C2SDropWarehouseItemPayload read(FriendlyByteBuf buf) {
        return new C2SDropWarehouseItemPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(slotId);
        buf.writeBoolean(dropFullStack);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

