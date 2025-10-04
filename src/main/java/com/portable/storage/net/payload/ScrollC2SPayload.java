package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ScrollC2SPayload(float value) implements CustomPayload {
	public static final Id<ScrollC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "scroll"));
	public static final PacketCodec<RegistryByteBuf, ScrollC2SPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.FLOAT, ScrollC2SPayload::value,
		ScrollC2SPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


