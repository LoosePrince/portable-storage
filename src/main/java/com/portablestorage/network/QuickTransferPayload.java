package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record QuickTransferPayload(int slotId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuickTransferPayload> ID = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PortableStorage.MOD_ID, "quick_transfer"));

    public static final StreamCodec<FriendlyByteBuf, QuickTransferPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeInt(payload.slotId),
        buf -> new QuickTransferPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

