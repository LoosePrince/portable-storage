package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 虚拟“瓶装经验”点击：button=0 左键(取出)，1 右键(存入)
 */
public record XpBottleClickC2SPayload(int button) implements CustomPayload {
    public static final Id<XpBottleClickC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "xp_bottle_click"));
    public static final PacketCodec<RegistryByteBuf, XpBottleClickC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, XpBottleClickC2SPayload::button,
            XpBottleClickC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}


