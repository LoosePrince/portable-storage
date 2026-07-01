package com.portablestorage.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.portablestorage.storage.sync.WarehouseSnapshot;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

class S2CWarehouseSnapshotPayloadTest {
    @Test
    void codecRoundTripsUuidAndWarehouseNbt() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000601");
        CompoundTag tag = new CompoundTag();
        tag.putInt("warehouse_schema_version", 2);
        tag.putInt("visibleRows", 8);
        tag.putString("ownerName", "Alice");

        S2CWarehouseSnapshotPayload payload = new S2CWarehouseSnapshotPayload(new WarehouseSnapshot(owner, tag));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        S2CWarehouseSnapshotPayload.CODEC.encode(buffer, payload);
        S2CWarehouseSnapshotPayload decoded = S2CWarehouseSnapshotPayload.CODEC.decode(buffer);

        assertEquals(owner, decoded.snapshot().ownerUuid());
        assertEquals(2, decoded.snapshot().warehouseData().getInt("warehouse_schema_version").orElseThrow());
        assertEquals(8, decoded.snapshot().warehouseData().getInt("visibleRows").orElseThrow());
        assertEquals("Alice", decoded.snapshot().warehouseData().getString("ownerName").orElseThrow());
    }

    @Test
    void decoderNormalizesNullNbtToEmptyCompound() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000602");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUUID(owner);
        buffer.writeNbt(null);

        S2CWarehouseSnapshotPayload decoded = S2CWarehouseSnapshotPayload.CODEC.decode(buffer);

        assertEquals(owner, decoded.snapshot().ownerUuid());
        assertTrue(decoded.snapshot().warehouseData().isEmpty());
    }
}