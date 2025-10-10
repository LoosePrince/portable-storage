package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 请求服务端打开自定义工作台界面的简单 C2S 数据包（无负载）。
 */
public record RequestPortableCraftingOpenC2SPayload() implements CustomPayload {
    public static final Id<RequestPortableCraftingOpenC2SPayload> ID = new Id<>(Identifier.of("portable-storage", "request_portable_crafting_open"));

    public static final PacketCodec<RegistryByteBuf, RequestPortableCraftingOpenC2SPayload> PACKET_CODEC = PacketCodec.unit(new RequestPortableCraftingOpenC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}


