package com.portable.storage.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.portable.storage.PortableStorage.MOD_ID;

/**
 * 返还合成槽位物品载荷
 */
public record RefundCraftingSlotsC2SPayload() implements CustomPayload {
    public static final CustomPayload.Id<RefundCraftingSlotsC2SPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "refund_crafting_slots"));

    public static final PacketCodec<RegistryByteBuf, RefundCraftingSlotsC2SPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                // 空载荷，不需要写入任何数据
            },
            buf -> new RefundCraftingSlotsC2SPayload()
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
