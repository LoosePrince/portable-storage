package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Shift 点击仓库物品：
 * - 左键：直接取出一组到背包
 * - 右键：直接取出一个到背包
 */
public record StorageShiftTakeC2SPayload(int index, int button) implements CustomPayload {
    public static final Id<StorageShiftTakeC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_shift_take"));
    public static final PacketCodec<RegistryByteBuf, StorageShiftTakeC2SPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.INTEGER, StorageShiftTakeC2SPayload::index,
        PacketCodecs.INTEGER, StorageShiftTakeC2SPayload::button,
        StorageShiftTakeC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}


