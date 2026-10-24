package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record S2COpenFoodFilterPayload(List<String> filters, boolean blacklist) implements CustomPacketPayload {
    public static final Type<S2COpenFoodFilterPayload> TYPE = new Type<>(PortableStorage.id("open_food_filter"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenFoodFilterPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.filters.size());
            payload.filters.forEach(buf::writeUtf);
            buf.writeBoolean(payload.blacklist);
        },
        buf -> {
            int size = buf.readVarInt();
            List<String> filters = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                filters.add(buf.readUtf());
            }
            return new S2COpenFoodFilterPayload(filters, buf.readBoolean());
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
