package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateServerConfigPayload(
    boolean enable3x3Crafting,
    boolean dropStorageOnDeath,
    int maxStorageTypes,
    long maxItemStackSize,
    int baseMaxStorageTypes,
    long baseMaxItemStackSize
) implements CustomPacketPayload {
    public static final Type<UpdateServerConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PortableStorage.MOD_ID, "update_server_config"));

    public static final StreamCodec<FriendlyByteBuf, UpdateServerConfigPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.enable3x3Crafting);
            buf.writeBoolean(payload.dropStorageOnDeath);
            buf.writeInt(payload.maxStorageTypes);
            buf.writeLong(payload.maxItemStackSize);
            buf.writeInt(payload.baseMaxStorageTypes);
            buf.writeLong(payload.baseMaxItemStackSize);
        },
        buf -> new UpdateServerConfigPayload(
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

