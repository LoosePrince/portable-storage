package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 虚拟流体转换载荷
 */
public final class FluidConversionC2SPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "fluid_conversion");

    private final String fluidType;
    private final int button;

    public FluidConversionC2SPayload(String fluidType, int button) {
        this.fluidType = fluidType;
        this.button = button;
    }

    public String fluidType() { return fluidType; }
    public int button() { return button; }

    public static void write(PacketByteBuf buf, FluidConversionC2SPayload v) {
        buf.writeString(v.fluidType);
        buf.writeVarInt(v.button);
    }

    public static FluidConversionC2SPayload read(PacketByteBuf buf) {
        return new FluidConversionC2SPayload(buf.readString(), buf.readVarInt());
    }
}
