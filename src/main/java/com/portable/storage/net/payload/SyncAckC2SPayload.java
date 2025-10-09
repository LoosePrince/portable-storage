package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 同步确认数据包
 * 客户端确认收到增量同步数据
 */
public record SyncAckC2SPayload(
    long syncId,           // 确认的同步ID
    boolean success        // 是否成功处理
) implements CustomPayload {
    
    public static final Id<SyncAckC2SPayload> ID = 
        new Id<>(Identifier.of(PortableStorage.MOD_ID, "sync_ack"));
    
    public static final PacketCodec<RegistryByteBuf, SyncAckC2SPayload> CODEC = 
        PacketCodec.tuple(
            PacketCodecs.VAR_LONG, SyncAckC2SPayload::syncId,
            PacketCodecs.BOOL, SyncAckC2SPayload::success,
            SyncAckC2SPayload::new
        );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
