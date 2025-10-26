package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 服务器到客户端的打开绑定木桶筛选界面请求
 */
public record OpenBarrelFilterS2CPayload(BlockPos barrelPos) implements CustomPayload {
    public static final CustomPayload.Id<OpenBarrelFilterS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "open_barrel_filter"));

    public static final PacketCodec<RegistryByteBuf, OpenBarrelFilterS2CPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeBlockPos(value.barrelPos());
        },
        buf -> {
            return new OpenBarrelFilterS2CPayload(buf.readBlockPos());
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() { 
        return ID; 
    }
}
