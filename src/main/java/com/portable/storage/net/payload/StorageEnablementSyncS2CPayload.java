package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端到客户端的仓库启用状态同步包
 */
public record StorageEnablementSyncS2CPayload(boolean enabled) implements CustomPayload {
    public static final Id<StorageEnablementSyncS2CPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_enablement_sync"));
    public static final PacketCodec<RegistryByteBuf, StorageEnablementSyncS2CPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeBoolean(payload.enabled),
        buf -> new StorageEnablementSyncS2CPayload(buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
