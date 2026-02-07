package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

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
    int riftForcedLoadingRange
) implements FabricPacket {
    public static final PacketType<SyncConfigPayload> TYPE = PacketType.create(
        PortableStorage.id("sync_config"), SyncConfigPayload::read
    );

    public SyncConfigPayload(FriendlyByteBuf buf) {
        this(
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
            buf.readInt()
        );
    }

    private static SyncConfigPayload read(FriendlyByteBuf buf) {
        return new SyncConfigPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(enable3x3Crafting);
        buf.writeBoolean(dropStorageOnDeath);
        buf.writeBoolean(allowHotReload);
        buf.writeInt(maxStorageTypes);
        buf.writeLong(maxItemStackSize);
        buf.writeInt(baseMaxStorageTypes);
        buf.writeLong(baseMaxItemStackSize);
        buf.writeInt(maxItemNbtSize);
        buf.writeUtf(unconditionalWarehouse);
        buf.writeUtf(baseWarehouseActivationItem);
        buf.writeUtf(fullWarehouseActivationItem);
        buf.writeInt(hopperRange);
        buf.writeDouble(hopperFrequency);
        buf.writeLong(lavaInfiniteThreshold);
        buf.writeLong(waterInfiniteThreshold);
        buf.writeUtf(riftUpgradeItem);
        buf.writeInt(riftChunkSize);
        buf.writeBoolean(enableRiftForcedLoading);
        buf.writeInt(riftForcedLoadingRange);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
