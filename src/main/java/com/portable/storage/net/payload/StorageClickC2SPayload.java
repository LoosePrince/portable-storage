package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StorageClickC2SPayload(int index) implements CustomPayload {
	public static final Id<StorageClickC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_click"));
	public static final PacketCodec<RegistryByteBuf, StorageClickC2SPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER, StorageClickC2SPayload::index,
		StorageClickC2SPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


