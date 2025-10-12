package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.portable.storage.PortableStorage.MOD_ID;

/**
 * 虚拟流体点击载荷
 */
public record FluidClickC2SPayload(String fluidType, int button) implements CustomPayload {
    public static final CustomPayload.Id<FluidClickC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "fluid_click"));

    public static final PacketCodec<RegistryByteBuf, FluidClickC2SPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.fluidType);
                buf.writeVarInt(value.button);
            },
            buf -> new FluidClickC2SPayload(buf.readString(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
