package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 客户端点击流体槽位的消息
 */
public final class FluidSlotClickC2SPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "fluid_slot_click");

    private final int button;

    public FluidSlotClickC2SPayload(int button) { this.button = button; }
    public int button() { return button; }

    public static void write(PacketByteBuf buf, FluidSlotClickC2SPayload v) {
        buf.writeVarInt(v.button);
    }

    public static FluidSlotClickC2SPayload read(PacketByteBuf buf) {
        return new FluidSlotClickC2SPayload(buf.readVarInt());
    }
}
