package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.portable.storage.PortableStorage.MOD_ID;

/**
 * 统一的同步控制（C2S）：REQUEST / ACK。
 * 为简化，ACK 使用 seq 字段承载（当前会话内的序号）。
 */
public record SyncControlC2SPayload(Op op, long syncId, boolean success) implements CustomPayload {
    public static final CustomPayload.Id<SyncControlC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "sync_control"));

    public static final PacketCodec<RegistryByteBuf, SyncControlC2SPayload> CODEC = PacketCodec.of(
        (v, buf) -> {
            buf.writeVarInt(v.op.ordinal());
            if (v.op == Op.ACK) {
                buf.writeVarLong(v.syncId);
                buf.writeBoolean(v.success);
            }
        },
        buf -> {
            Op op = Op.values()[buf.readVarInt()];
            if (op == Op.ACK) {
                long id = buf.readVarLong();
                boolean ok = buf.readBoolean();
                return new SyncControlC2SPayload(op, id, ok);
            }
            return new SyncControlC2SPayload(op, 0L, false);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public enum Op { REQUEST, ACK }
}


