package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class StorageSyncS2CPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "storage_sync");

    private final NbtCompound nbt;

    public StorageSyncS2CPayload(NbtCompound nbt) { this.nbt = nbt; }
    public NbtCompound nbt() { return nbt; }

    public static void write(PacketByteBuf buf, StorageSyncS2CPayload value) {
        buf.writeNbt(value.nbt);
    }

    public static StorageSyncS2CPayload read(PacketByteBuf buf) {
        return new StorageSyncS2CPayload(buf.readNbt());
    }
}


