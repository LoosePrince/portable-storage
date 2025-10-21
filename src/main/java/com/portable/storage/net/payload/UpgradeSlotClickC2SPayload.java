package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端点击升级槽位的消息
 */
public record UpgradeSlotClickC2SPayload(int slot, int button) implements CustomPayload {
	public static final CustomPayload.Id<UpgradeSlotClickC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "upgrade_slot_click"));

	public static final PacketCodec<RegistryByteBuf, UpgradeSlotClickC2SPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.slot);
				buf.writeVarInt(value.button);
			},
			buf -> new UpgradeSlotClickC2SPayload(buf.readVarInt(), buf.readVarInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

