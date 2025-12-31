package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncConfigPayload(
    boolean enable3x3Crafting,
    boolean dropStorageOnDeath,
    boolean allowHotReload,
    int maxStorageTypes,
    long maxItemStackSize,
    int baseMaxStorageTypes,
    long baseMaxItemStackSize,
    String unconditionalWarehouse,
    int hopperRange,
    double hopperFrequency
) implements CustomPacketPayload {
    public static final Type<SyncConfigPayload> TYPE = new Type<>(PortableStorage.id("sync_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.enable3x3Crafting);
            buf.writeBoolean(payload.dropStorageOnDeath);
            buf.writeBoolean(payload.allowHotReload);
            buf.writeInt(payload.maxStorageTypes);
            buf.writeLong(payload.maxItemStackSize);
            buf.writeInt(payload.baseMaxStorageTypes);
            buf.writeLong(payload.baseMaxItemStackSize);
            buf.writeUtf(payload.unconditionalWarehouse);
            buf.writeInt(payload.hopperRange);
            buf.writeDouble(payload.hopperFrequency);
        },
        buf -> new SyncConfigPayload(
            buf.readBoolean(), 
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readInt(),
            buf.readLong(),
            buf.readInt(),
            buf.readLong(),
            buf.readUtf(),
            buf.readInt(),
            buf.readDouble()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
