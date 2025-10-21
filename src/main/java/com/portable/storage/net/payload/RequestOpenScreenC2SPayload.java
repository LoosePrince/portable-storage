package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 统一的打开界面请求（C2S）。
 */
public record RequestOpenScreenC2SPayload(Screen screen, BlockPos pos, String dimensionId) implements CustomPayload {
    public static final CustomPayload.Id<RequestOpenScreenC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "request_open_screen"));

    public static final PacketCodec<RegistryByteBuf, RequestOpenScreenC2SPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.screen.ordinal());
            if (value.pos != null) {
                buf.writeBoolean(true);
                buf.writeBlockPos(value.pos);
            } else buf.writeBoolean(false);
            buf.writeString(value.dimensionId != null ? value.dimensionId : "");
        },
        buf -> {
            Screen s = Screen.values()[buf.readVarInt()];
            BlockPos p = buf.readBoolean() ? buf.readBlockPos() : null;
            String dim = buf.readString();
            return new RequestOpenScreenC2SPayload(s, p, dim.isEmpty() ? null : dim);
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public enum Screen { VANILLA_CRAFTING, PORTABLE_CRAFTING }
}


