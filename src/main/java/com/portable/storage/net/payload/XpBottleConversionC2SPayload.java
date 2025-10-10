package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record XpBottleConversionC2SPayload(int button) implements CustomPayload {
    public static final Id<XpBottleConversionC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "xp_bottle_conversion"));
    public static final PacketCodec<RegistryByteBuf, XpBottleConversionC2SPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.INTEGER, XpBottleConversionC2SPayload::button,
        XpBottleConversionC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
