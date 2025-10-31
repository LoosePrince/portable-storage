package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class XpBottleMaintenanceToggleC2SPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "xp_bottle_maintenance_toggle");
    public static void write(PacketByteBuf buf, XpBottleMaintenanceToggleC2SPayload v) { }
    public static XpBottleMaintenanceToggleC2SPayload read(PacketByteBuf buf) { return new XpBottleMaintenanceToggleC2SPayload(); }
}
