package com.portablestorage.network;

import com.portablestorage.PortableStorage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SUpgradeInteractionPayload(
        Identifier upgradeId,
        int button // 1: right, 2: middle
) implements CustomPacketPayload {
    public static final Type<C2SUpgradeInteractionPayload> TYPE = new Type<>(PortableStorage.id("upgrade_interaction"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpgradeInteractionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeIdentifier(payload.upgradeId());
                buf.writeInt(payload.button());
            },
            buf -> new C2SUpgradeInteractionPayload(
                    buf.readIdentifier(),
                    buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
