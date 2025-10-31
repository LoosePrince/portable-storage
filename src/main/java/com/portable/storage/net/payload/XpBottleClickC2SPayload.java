package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 虚拟“瓶装经验”点击：button=0 左键(取出)，1 右键(存入)
 */
public final class XpBottleClickC2SPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "xp_bottle_click");

    private final int button;
    public XpBottleClickC2SPayload(int button) { this.button = button; }
    public int button() { return button; }

    public static void write(PacketByteBuf buf, XpBottleClickC2SPayload v) {
        buf.writeVarInt(v.button);
    }

    public static XpBottleClickC2SPayload read(PacketByteBuf buf) {
        return new XpBottleClickC2SPayload(buf.readVarInt());
    }
}


