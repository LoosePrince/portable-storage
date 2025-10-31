package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 客户端点击升级槽位的消息
 */
public final class UpgradeSlotClickC2SPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "upgrade_slot_click");
    private final int slot;
    private final int button;
    public UpgradeSlotClickC2SPayload(int slot, int button) { this.slot = slot; this.button = button; }
    public int slot() { return slot; }
    public int button() { return button; }
    public static void write(PacketByteBuf buf, UpgradeSlotClickC2SPayload v) { buf.writeVarInt(v.slot); buf.writeVarInt(v.button); }
    public static UpgradeSlotClickC2SPayload read(PacketByteBuf buf) { return new UpgradeSlotClickC2SPayload(buf.readVarInt(), buf.readVarInt()); }
}

