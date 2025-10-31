package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 统一的打开界面请求（C2S）。
 */
public final class RequestOpenScreenC2SPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "request_open_screen");

    public enum Screen { VANILLA_CRAFTING, PORTABLE_CRAFTING, FILTER_MAIN, FILTER_SCREEN, DESTROY_SCREEN, BARREL_FILTER }

    private final Screen screen;
    private final BlockPos pos;
    private final String dimensionId;

    public RequestOpenScreenC2SPayload(Screen screen, BlockPos pos, String dimensionId) {
        this.screen = screen;
        this.pos = pos;
        this.dimensionId = dimensionId;
    }

    public Screen screen() { return screen; }
    public BlockPos pos() { return pos; }
    public String dimensionId() { return dimensionId; }

    public static void write(PacketByteBuf buf, RequestOpenScreenC2SPayload v) {
        buf.writeVarInt(v.screen.ordinal());
        if (v.pos != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(v.pos);
        } else buf.writeBoolean(false);
        buf.writeString(v.dimensionId != null ? v.dimensionId : "");
    }

    public static RequestOpenScreenC2SPayload read(PacketByteBuf buf) {
        Screen s = Screen.values()[buf.readVarInt()];
        BlockPos p = buf.readBoolean() ? buf.readBlockPos() : null;
        String dim = buf.readString();
        return new RequestOpenScreenC2SPayload(s, p, dim.isEmpty() ? null : dim);
    }
}


