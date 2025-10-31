package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 统一的同步控制（C2S）：REQUEST / ACK。
 */
public final class SyncControlC2SPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "sync_control");

    public enum Op { REQUEST, ACK }

    private final Op op;
    private final long syncId;
    private final boolean success;

    public SyncControlC2SPayload(Op op, long syncId, boolean success) {
        this.op = op;
        this.syncId = syncId;
        this.success = success;
    }

    public Op op() { return op; }
    public long syncId() { return syncId; }
    public boolean success() { return success; }

    public static void write(PacketByteBuf buf, SyncControlC2SPayload v) {
        buf.writeVarInt(v.op.ordinal());
        if (v.op == Op.ACK) {
            buf.writeVarLong(v.syncId);
            buf.writeBoolean(v.success);
        }
    }

    public static SyncControlC2SPayload read(PacketByteBuf buf) {
        Op op = Op.values()[buf.readVarInt()];
        if (op == Op.ACK) {
            long id = buf.readVarLong();
            boolean ok = buf.readBoolean();
            return new SyncControlC2SPayload(op, id, ok);
        }
        return new SyncControlC2SPayload(op, 0L, false);
    }
}


