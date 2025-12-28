package com.portablestorage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncConfigPayload(
    boolean enable3x3Crafting, 
    boolean dropStorageOnDeath,
    int maxStorageTypes,
    long maxItemStackSize,
    int baseMaxStorageTypes,
    long baseMaxItemStackSize
) implements CustomPacketPayload {
    public static final Type<SyncConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "sync_config"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.enable3x3Crafting);
            buf.writeBoolean(payload.dropStorageOnDeath);
            buf.writeInt(payload.maxStorageTypes);
            buf.writeLong(payload.maxItemStackSize);
            buf.writeInt(payload.baseMaxStorageTypes);
            buf.writeLong(payload.baseMaxItemStackSize);
        },
        buf -> new SyncConfigPayload(
            buf.readBoolean(), 
            buf.readBoolean(),
            buf.readInt(),
            buf.readLong(),
            buf.readInt(),
            buf.readLong()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
