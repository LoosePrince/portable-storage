package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class XpBottleConversionC2SPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "xp_bottle_conversion");
    private final int button;
    public XpBottleConversionC2SPayload(int button) { this.button = button; }
    public int button() { return button; }
    public static void write(PacketByteBuf buf, XpBottleConversionC2SPayload v) { buf.writeVarInt(v.button); }
    public static XpBottleConversionC2SPayload read(PacketByteBuf buf) { return new XpBottleConversionC2SPayload(buf.readVarInt()); }
}
