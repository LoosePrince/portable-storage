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
    int maxItemNbtSize,
    String unconditionalWarehouse,
    String baseWarehouseActivationItem,
    String fullWarehouseActivationItem,
    int hopperRange,
    double hopperFrequency,
    long lavaInfiniteThreshold,
    long waterInfiniteThreshold,
    String riftUpgradeItem,
    int riftChunkSize,
    boolean enableRiftForcedLoading,
    int riftForcedLoadingRange,
    boolean enableConduitUpgrade
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
            buf.writeInt(payload.maxItemNbtSize);
            buf.writeUtf(payload.unconditionalWarehouse);
            buf.writeUtf(payload.baseWarehouseActivationItem);
            buf.writeUtf(payload.fullWarehouseActivationItem);
            buf.writeInt(payload.hopperRange);
            buf.writeDouble(payload.hopperFrequency);
            buf.writeLong(payload.lavaInfiniteThreshold);
            buf.writeLong(payload.waterInfiniteThreshold);
            buf.writeUtf(payload.riftUpgradeItem);
            buf.writeInt(payload.riftChunkSize);
            buf.writeBoolean(payload.enableRiftForcedLoading);
            buf.writeInt(payload.riftForcedLoadingRange);
            buf.writeBoolean(payload.enableConduitUpgrade);
        },
        buf -> new SyncConfigPayload(
            buf.readBoolean(), 
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readInt(),
            buf.readLong(),
            buf.readInt(),
            buf.readLong(),
            buf.readInt(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readDouble(),
            buf.readLong(),
            buf.readLong(),
            buf.readUtf(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readInt(),
            buf.readBoolean()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
