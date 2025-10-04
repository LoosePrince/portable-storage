package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestSyncC2SPayload() implements CustomPayload {
	public static final Id<RequestSyncC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "request_sync"));
	public static final RequestSyncC2SPayload INSTANCE = new RequestSyncC2SPayload();
	public static final PacketCodec<RegistryByteBuf, RequestSyncC2SPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


