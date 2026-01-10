package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

import java.util.ArrayList;
import java.util.List;

public record S2COpenFoodFilterPayload(List<String> filters, boolean blacklist) implements FabricPacket {
    public static final PacketType<S2COpenFoodFilterPayload> TYPE = PacketType.create(
        PortableStorage.id("open_food_filter"), S2COpenFoodFilterPayload::read
    );

    public S2COpenFoodFilterPayload(FriendlyByteBuf buf) {
        this(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf), buf.readBoolean());
    }

    private static S2COpenFoodFilterPayload read(FriendlyByteBuf buf) {
        return new S2COpenFoodFilterPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(filters, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(blacklist);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
