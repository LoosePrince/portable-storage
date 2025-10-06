package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 请求服务端打开原版工作台界面的简单 C2S 数据包（无负载）。
 */
public record RequestVanillaCraftingOpenC2SPayload() implements CustomPayload {
    public static final Id<RequestVanillaCraftingOpenC2SPayload> ID = new Id<>(Identifier.of("portable-storage", "request_vanilla_crafting_open"));

    public static final PacketCodec<RegistryByteBuf, RequestVanillaCraftingOpenC2SPayload> PACKET_CODEC = PacketCodec.unit(new RequestVanillaCraftingOpenC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}


