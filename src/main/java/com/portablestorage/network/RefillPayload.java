package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record RefillPayload(java.util.List<Integer> slotIds, ItemStack targetStack) implements CustomPacketPayload {
    public static final Type<RefillPayload> TYPE = new Type<>(PortableStorage.id("refill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefillPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.slotIds.size());
            payload.slotIds.forEach(buf::writeInt);
            ItemStack.STREAM_CODEC.encode(buf, payload.targetStack);
        },
        buf -> {
            int size = buf.readVarInt();
            java.util.List<Integer> slotIds = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                slotIds.add(buf.readInt());
            }
            return new RefillPayload(slotIds, ItemStack.STREAM_CODEC.decode(buf));
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

