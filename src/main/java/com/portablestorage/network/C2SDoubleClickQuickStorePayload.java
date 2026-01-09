package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SDoubleClickQuickStorePayload() implements CustomPacketPayload {
    public static final Type<C2SDoubleClickQuickStorePayload> TYPE = new Type<>(PortableStorage.id("double_click_quick_store"));

    public static final StreamCodec<FriendlyByteBuf, C2SDoubleClickQuickStorePayload> CODEC = StreamCodec.unit(new C2SDoubleClickQuickStorePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
