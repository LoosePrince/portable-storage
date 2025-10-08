package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 从仓库直接丢出悬停物品（Q/CTRL+Q）。
 * amountType: 0 = 一个, 1 = 一组
 */
public record StorageDropC2SPayload(int index, int amountType) implements CustomPayload {
    public static final Id<StorageDropC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_drop"));
    public static final PacketCodec<RegistryByteBuf, StorageDropC2SPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.INTEGER, StorageDropC2SPayload::index,
        PacketCodecs.INTEGER, StorageDropC2SPayload::amountType,
        StorageDropC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}


