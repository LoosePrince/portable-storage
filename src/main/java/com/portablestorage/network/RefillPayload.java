package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record RefillPayload(java.util.List<Integer> slotIds, ItemStack targetStack) implements CustomPacketPayload {
    public static final Type<RefillPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PortableStorage.MOD_ID, "refill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefillPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeCollection(payload.slotIds, (b, id) -> b.writeInt(id));
            ItemStack.STREAM_CODEC.encode(buf, payload.targetStack);
        },
        buf -> new RefillPayload(buf.readCollection(java.util.ArrayList::new, b -> b.readInt()), ItemStack.STREAM_CODEC.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

