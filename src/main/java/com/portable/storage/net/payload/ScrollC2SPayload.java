package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class ScrollC2SPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "scroll");
    private final float value;
    public ScrollC2SPayload(float value) { this.value = value; }
    public float value() { return value; }
    public static void write(PacketByteBuf buf, ScrollC2SPayload v) { buf.writeFloat(v.value); }
    public static ScrollC2SPayload read(PacketByteBuf buf) { return new ScrollC2SPayload(buf.readFloat()); }
}


