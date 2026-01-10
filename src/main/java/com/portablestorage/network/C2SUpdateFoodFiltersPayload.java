package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

import java.util.ArrayList;
import java.util.List;

public record C2SUpdateFoodFiltersPayload(List<String> filters, boolean blacklist) implements FabricPacket {
    public static final PacketType<C2SUpdateFoodFiltersPayload> TYPE = PacketType.create(
        PortableStorage.id("update_food_filters"), C2SUpdateFoodFiltersPayload::read
    );

    public C2SUpdateFoodFiltersPayload(FriendlyByteBuf buf) {
        this(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf), buf.readBoolean());
    }

    private static C2SUpdateFoodFiltersPayload read(FriendlyByteBuf buf) {
        return new C2SUpdateFoodFiltersPayload(buf);
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
