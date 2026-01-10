package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

public record OpenCraftingPayload() implements FabricPacket {
    public static final PacketType<OpenCraftingPayload> TYPE = PacketType.create(
        PortableStorage.id("open_crafting"), OpenCraftingPayload::read
    );

    public OpenCraftingPayload(FriendlyByteBuf buf) {
        this();
    }

    private static OpenCraftingPayload read(FriendlyByteBuf buf) {
        return new OpenCraftingPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        // 无数据需要写入
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

