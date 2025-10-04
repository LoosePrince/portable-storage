package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DepositCursorC2SPayload() implements CustomPayload {
	public static final Id<DepositCursorC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "deposit_cursor"));
	public static final DepositCursorC2SPayload INSTANCE = new DepositCursorC2SPayload();
	public static final PacketCodec<RegistryByteBuf, DepositCursorC2SPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


