package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RefillCraftingC2SPayload(int slotIndex, ItemStack targetStack) implements CustomPayload {
	public static final Id<RefillCraftingC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "refill_crafting"));
	public static final PacketCodec<RegistryByteBuf, RefillCraftingC2SPayload> CODEC = PacketCodec.tuple(
		PacketCodecs.INTEGER, RefillCraftingC2SPayload::slotIndex,
		ItemStack.PACKET_CODEC, RefillCraftingC2SPayload::targetStack,
		RefillCraftingC2SPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}

