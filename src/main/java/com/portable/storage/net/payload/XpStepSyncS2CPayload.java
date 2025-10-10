package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record XpStepSyncS2CPayload(int stepIndex) implements CustomPayload {
    public static final Id<XpStepSyncS2CPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "xp_step_sync"));
    public static final PacketCodec<RegistryByteBuf, XpStepSyncS2CPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.INTEGER, XpStepSyncS2CPayload::stepIndex,
        XpStepSyncS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
