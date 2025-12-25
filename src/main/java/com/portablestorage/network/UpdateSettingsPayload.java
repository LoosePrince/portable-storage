package com.portablestorage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateSettingsPayload(int settingType, int value) implements CustomPacketPayload {
    public static final Type<UpdateSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("portablestorage", "update_settings"));
    
    // settingType: 0=折叠, 1=排序模式, 2=排序顺序, 3=快捷交互
    
    public static final StreamCodec<FriendlyByteBuf, UpdateSettingsPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.settingType);
            buf.writeInt(payload.value);
        },
        buf -> new UpdateSettingsPayload(buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
