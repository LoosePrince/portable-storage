package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SDropWarehouseItemPayload(int slotId, boolean dropFullStack) implements CustomPacketPayload {
    public static final Type<C2SDropWarehouseItemPayload> TYPE = new Type<>(PortableStorage.id("drop_warehouse_item"));

    public static final StreamCodec<FriendlyByteBuf, C2SDropWarehouseItemPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.slotId);
            buf.writeBoolean(payload.dropFullStack);
        },
        buf -> new C2SDropWarehouseItemPayload(buf.readInt(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

