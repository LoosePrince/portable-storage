package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public record StorageSyncS2CPayload(NbtCompound nbt) implements CustomPayload {
	public static final Id<StorageSyncS2CPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_sync"));
	public static final PacketCodec<RegistryByteBuf, StorageSyncS2CPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.NBT_COMPOUND, StorageSyncS2CPayload::nbt,
		StorageSyncS2CPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


