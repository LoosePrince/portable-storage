package com.portablestorage.network;

import com.portablestorage.util.WarehouseSetting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateSettingsPayload(WarehouseSetting setting, int value) implements CustomPacketPayload {
    public static final Type<UpdateSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "update_settings"));
    
    public static final StreamCodec<FriendlyByteBuf, UpdateSettingsPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeEnum(payload.setting);
            buf.writeInt(payload.value);
        },
        buf -> new UpdateSettingsPayload(buf.readEnum(WarehouseSetting.class), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
