package com.portable.storage.net.payload;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.portable.storage.PortableStorage.MOD_ID;

/**
 * 服务器同步升级槽位数据到客户端
 */
public record UpgradeSyncS2CPayload(NbtCompound data) implements CustomPayload {
	public static final CustomPayload.Id<UpgradeSyncS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "upgrade_sync"));

	public static final PacketCodec<RegistryByteBuf, UpgradeSyncS2CPayload> CODEC = PacketCodec.of(
			(value, buf) -> buf.writeNbt(value.data),
			buf -> new UpgradeSyncS2CPayload(buf.readNbt())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

