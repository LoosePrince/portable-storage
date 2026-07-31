package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuickTransferPayload(int slotId, boolean isWarehouseSlot, boolean isUpgradeSlot) implements CustomPacketPayload {
    public static final Type<QuickTransferPayload> TYPE = new Type<>(PortableStorage.id("quick_transfer"));

    public static final StreamCodec<FriendlyByteBuf, QuickTransferPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.slotId());
            buf.writeBoolean(payload.isWarehouseSlot());
            buf.writeBoolean(payload.isUpgradeSlot());
        },
        buf -> new QuickTransferPayload(buf.readInt(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}