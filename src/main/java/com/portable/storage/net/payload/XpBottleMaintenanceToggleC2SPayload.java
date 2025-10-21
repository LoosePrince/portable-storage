package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record XpBottleMaintenanceToggleC2SPayload() implements CustomPayload {
    public static final Id<XpBottleMaintenanceToggleC2SPayload> ID = new Id<>(net.minecraft.util.Identifier.of(PortableStorage.MOD_ID, "xp_bottle_maintenance_toggle"));
    public static final PacketCodec<RegistryByteBuf, XpBottleMaintenanceToggleC2SPayload> CODEC = PacketCodec.unit(new XpBottleMaintenanceToggleC2SPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
