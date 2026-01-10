package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record RefillPayload(List<Integer> slotIds, ItemStack targetStack) implements FabricPacket {
    public static final PacketType<RefillPayload> TYPE = PacketType.create(
        PortableStorage.id("refill"), RefillPayload::read
    );

    public RefillPayload(FriendlyByteBuf buf) {
        this(
            buf.readCollection(ArrayList::new, FriendlyByteBuf::readInt),
            buf.readItem()
        );
    }

    private static RefillPayload read(FriendlyByteBuf buf) {
        return new RefillPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(slotIds, FriendlyByteBuf::writeInt);
        buf.writeItem(targetStack);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

