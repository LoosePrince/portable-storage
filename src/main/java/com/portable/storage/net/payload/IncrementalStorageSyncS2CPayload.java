package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 增量同步（S2C）：包含会话与序号，用于按序应用 diff。
 * diff 的具体结构由 NBT 承载，客户端自行解析。
 */
public record IncrementalStorageSyncS2CPayload(long sessionId, int seq, NbtCompound diff) implements CustomPayload {
    public static final Id<IncrementalStorageSyncS2CPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_sync_incremental"));

    public static final PacketCodec<RegistryByteBuf, IncrementalStorageSyncS2CPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.VAR_LONG, IncrementalStorageSyncS2CPayload::sessionId,
        PacketCodecs.VAR_INT, IncrementalStorageSyncS2CPayload::seq,
        PacketCodecs.NBT_COMPOUND, IncrementalStorageSyncS2CPayload::diff,
        IncrementalStorageSyncS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

 