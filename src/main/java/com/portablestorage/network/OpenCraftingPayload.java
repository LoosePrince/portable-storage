package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenCraftingPayload() implements CustomPacketPayload {
    public static final Type<OpenCraftingPayload> TYPE = new Type<>(PortableStorage.id("open_crafting"));

    public static final StreamCodec<FriendlyByteBuf, OpenCraftingPayload> CODEC = StreamCodec.unit(new OpenCraftingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

