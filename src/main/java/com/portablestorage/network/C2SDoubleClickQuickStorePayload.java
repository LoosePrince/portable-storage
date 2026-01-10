package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record C2SDoubleClickQuickStorePayload() implements FabricPacket {
    public static final PacketType<C2SDoubleClickQuickStorePayload> TYPE = PacketType.create(
        PortableStorage.id("double_click_quick_store"), C2SDoubleClickQuickStorePayload::read
    );

    public C2SDoubleClickQuickStorePayload(FriendlyByteBuf buf) {
        this();
    }

    private static C2SDoubleClickQuickStorePayload read(FriendlyByteBuf buf) {
        return new C2SDoubleClickQuickStorePayload(buf);
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
