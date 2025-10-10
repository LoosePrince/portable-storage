package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 双击拾取：让虚拟合成从 1..9 槽位向光标合并相同物品，直到满堆。
 */
public record OverlayCraftingDoubleClickC2SPayload() implements CustomPayload {
    public static final Id<OverlayCraftingDoubleClickC2SPayload> ID = new Id<>(Identifier.of("portable-storage", "overlay_crafting_double_click"));
    public static final PacketCodec<RegistryByteBuf, OverlayCraftingDoubleClickC2SPayload> PACKET_CODEC = PacketCodec.unit(new OverlayCraftingDoubleClickC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

 