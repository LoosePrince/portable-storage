package com.portable.storage.net.payload;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.portable.storage.PortableStorage.MOD_ID;

/**
 * 统一的配置/状态同步（S2C）：
 * topic 指明数据主题，data 承载具体 NBT。
 */
public record ConfigSyncS2CPayload(Topic topic, NbtCompound data) implements CustomPayload {
    public static final CustomPayload.Id<ConfigSyncS2CPayload> ID = new CustomPayload.Id<>(Identifier.of(MOD_ID, "config_sync"));

    public static final PacketCodec<RegistryByteBuf, ConfigSyncS2CPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.topic.ordinal());
            buf.writeNbt(value.data);
        },
        buf -> new ConfigSyncS2CPayload(Topic.values()[buf.readVarInt()], buf.readNbt())
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public enum Topic {
        DISPLAY_CONFIG,
        STORAGE_ENABLEMENT,
        UPGRADE,
        XP_STEP,
        RIFT_CONFIG,
        VIRTUAL_CRAFTING_CONFIG
    }
}


