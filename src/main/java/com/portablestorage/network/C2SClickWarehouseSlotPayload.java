package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SClickWarehouseSlotPayload(int containerSlot, int button, boolean isUpgradeSlot) implements CustomPacketPayload {
    public static final Type<C2SClickWarehouseSlotPayload> TYPE = new Type<>(PortableStorage.id("click_warehouse_slot"));

    public static final StreamCodec<FriendlyByteBuf, C2SClickWarehouseSlotPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.containerSlot());
            buf.writeInt(payload.button());
            buf.writeBoolean(payload.isUpgradeSlot());
        },
        buf -> new C2SClickWarehouseSlotPayload(buf.readInt(), buf.readInt(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}