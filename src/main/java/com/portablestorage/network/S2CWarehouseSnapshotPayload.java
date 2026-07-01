package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import com.portablestorage.storage.sync.WarehouseSnapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CWarehouseSnapshotPayload(WarehouseSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<S2CWarehouseSnapshotPayload> TYPE =
            new Type<>(PortableStorage.id("warehouse_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, S2CWarehouseSnapshotPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUUID(payload.snapshot.ownerUuid());
                        buf.writeNbt(payload.snapshot.warehouseData());
                    },
                    buf -> {
                        java.util.UUID ownerUuid = buf.readUUID();
                        CompoundTag tag = buf.readNbt();
                        return new S2CWarehouseSnapshotPayload(
                                new WarehouseSnapshot(ownerUuid, tag == null ? new CompoundTag() : tag));
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}