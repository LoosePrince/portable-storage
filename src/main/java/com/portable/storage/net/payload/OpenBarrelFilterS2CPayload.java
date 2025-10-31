package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 服务器到客户端的打开绑定木桶筛选界面请求
 */
public final class OpenBarrelFilterS2CPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "open_barrel_filter");
    private final BlockPos barrelPos;
    public OpenBarrelFilterS2CPayload(BlockPos barrelPos) { this.barrelPos = barrelPos; }
    public BlockPos barrelPos() { return barrelPos; }
    public static void write(PacketByteBuf buf, OpenBarrelFilterS2CPayload v) { buf.writeBlockPos(v.barrelPos); }
    public static OpenBarrelFilterS2CPayload read(PacketByteBuf buf) { return new OpenBarrelFilterS2CPayload(buf.readBlockPos()); }
}
