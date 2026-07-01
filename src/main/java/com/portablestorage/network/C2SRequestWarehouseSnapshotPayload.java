package com.portablestorage.network;

import com.portablestorage.PortableStorage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SRequestWarehouseSnapshotPayload() implements CustomPacketPayload {
    public static final Type<C2SRequestWarehouseSnapshotPayload> TYPE =
            new Type<>(PortableStorage.id("request_warehouse_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, C2SRequestWarehouseSnapshotPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                    },
                    buf -> new C2SRequestWarehouseSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}