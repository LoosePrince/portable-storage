package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 仓库槽位点击：index 槽位，button=0 左键，1 右键。
 */
public record StorageSlotClickC2SPayload(int index, int button) implements CustomPayload {
	public static final Id<StorageSlotClickC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_slot_click"));
	public static final PacketCodec<RegistryByteBuf, StorageSlotClickC2SPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER, StorageSlotClickC2SPayload::index,
		PacketCodecs.INTEGER, StorageSlotClickC2SPayload::button,
		StorageSlotClickC2SPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}


