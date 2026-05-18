package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record UpdateServerConfigPayload(
    boolean enable3x3Crafting,
    boolean dropStorageOnDeath,
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
    int riftPlotSpacingChunks,
    int riftFloorY,
    boolean enableRiftForcedLoading,
    int riftForcedLoadingRange,
    boolean enableRiftAvatar,
    boolean enableRiftBorder,
    int riftBorderWarningBlocks,
    boolean enableConduitUpgrade
) implements CustomPacketPayload {
    public static final Type<UpdateServerConfigPayload> TYPE = new Type<>(PortableStorage.id("update_server_config"));

    public static final StreamCodec<FriendlyByteBuf, UpdateServerConfigPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.enable3x3Crafting);
            buf.writeBoolean(payload.dropStorageOnDeath);
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
            buf.writeInt(payload.riftPlotSpacingChunks);
            buf.writeInt(payload.riftFloorY);
            buf.writeBoolean(payload.enableRiftForcedLoading);
            buf.writeInt(payload.riftForcedLoadingRange);
            buf.writeBoolean(payload.enableRiftAvatar);
            buf.writeBoolean(payload.enableRiftBorder);
            buf.writeInt(payload.riftBorderWarningBlocks);
            buf.writeBoolean(payload.enableConduitUpgrade);
        },
        buf -> new UpdateServerConfigPayload(
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
            buf.readInt(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readInt(),
            buf.readBoolean(),
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
