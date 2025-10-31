package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 增量同步（S2C）：包含会话与序号，用于按序应用 diff。
 * diff 的具体结构由 NBT 承载，客户端自行解析。
 */
public final class IncrementalStorageSyncS2CPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "storage_sync_incremental");

    private final long sessionId;
    private final int seq;
    private final NbtCompound diff;

    public IncrementalStorageSyncS2CPayload(long sessionId, int seq, NbtCompound diff) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.diff = diff;
    }

    public long sessionId() { return sessionId; }
    public int seq() { return seq; }
    public NbtCompound diff() { return diff; }

    public static void write(PacketByteBuf buf, IncrementalStorageSyncS2CPayload v) {
        buf.writeVarLong(v.sessionId);
        buf.writeVarInt(v.seq);
        buf.writeNbt(v.diff);
    }

    public static IncrementalStorageSyncS2CPayload read(PacketByteBuf buf) {
        long sid = buf.readVarLong();
        int seq = buf.readVarInt();
        NbtCompound diff = buf.readNbt();
        return new IncrementalStorageSyncS2CPayload(sid, seq, diff);
    }
}

 