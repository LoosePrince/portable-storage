package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

public record C2SUpgradeInteractionPayload(
    ResourceLocation upgradeId,
    int button // 1: right, 2: middle
) implements FabricPacket {
    public static final PacketType<C2SUpgradeInteractionPayload> TYPE = PacketType.create(
        PortableStorage.id("upgrade_interaction"), C2SUpgradeInteractionPayload::read
    );

    public C2SUpgradeInteractionPayload(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readInt());
    }

    private static C2SUpgradeInteractionPayload read(FriendlyByteBuf buf) {
        return new C2SUpgradeInteractionPayload(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(upgradeId);
        buf.writeInt(button);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

