package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端点击流体槽位的消息
 */
public record FluidSlotClickC2SPayload(int button) implements CustomPayload {
	public static final CustomPayload.Id<FluidSlotClickC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "fluid_slot_click"));

	public static final PacketCodec<RegistryByteBuf, FluidSlotClickC2SPayload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeVarInt(value.button);
			},
			buf -> new FluidSlotClickC2SPayload(buf.readVarInt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
