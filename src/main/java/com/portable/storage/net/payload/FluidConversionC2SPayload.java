package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.portable.storage.PortableStorage.MOD_ID;

/**
 * 虚拟流体转换载荷
 */
public record FluidConversionC2SPayload(String fluidType, int button) implements CustomPayload {
    public static final CustomPayload.Id<FluidConversionC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "fluid_conversion"));

    public static final PacketCodec<RegistryByteBuf, FluidConversionC2SPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.fluidType);
                buf.writeVarInt(value.button);
            },
            buf -> new FluidConversionC2SPayload(buf.readString(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
