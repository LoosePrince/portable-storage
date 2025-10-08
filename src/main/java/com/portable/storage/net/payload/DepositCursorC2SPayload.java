package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DepositCursorC2SPayload(int button) implements CustomPayload {
	public static final Id<DepositCursorC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "deposit_cursor"));
	public static final PacketCodec<RegistryByteBuf, DepositCursorC2SPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER, DepositCursorC2SPayload::button,
		DepositCursorC2SPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


