package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DepositSlotC2SPayload(int handlerSlotId) implements CustomPayload {
	public static final Id<DepositSlotC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "deposit_slot"));
	public static final PacketCodec<RegistryByteBuf, DepositSlotC2SPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER, DepositSlotC2SPayload::handlerSlotId,
		DepositSlotC2SPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


