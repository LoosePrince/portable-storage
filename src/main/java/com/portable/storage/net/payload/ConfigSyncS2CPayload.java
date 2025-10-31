package com.portable.storage.net.payload;

import static com.portable.storage.PortableStorage.MOD_ID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 统一的配置/状态同步（S2C）：
 * topic 指明数据主题，data 承载具体 NBT。
 */
public final class ConfigSyncS2CPayload {
    public static final Identifier ID = new Identifier(MOD_ID, "config_sync");

    public enum Topic {
        DISPLAY_CONFIG,
        STORAGE_ENABLEMENT,
        UPGRADE,
        XP_STEP,
        RIFT_CONFIG,
        VIRTUAL_CRAFTING_CONFIG,
        INFINITE_FLUID_CONFIG,
        AUTO_EAT_MODE
    }

    private final Topic topic;
    private final NbtCompound data;

    public ConfigSyncS2CPayload(Topic topic, NbtCompound data) {
        this.topic = topic;
        this.data = data;
    }

    public Topic topic() { return topic; }
    public NbtCompound data() { return data; }

    public static void write(PacketByteBuf buf, ConfigSyncS2CPayload value) {
        buf.writeVarInt(value.topic.ordinal());
        buf.writeNbt(value.data);
    }

    public static ConfigSyncS2CPayload read(PacketByteBuf buf) {
        Topic t = Topic.values()[buf.readVarInt()];
        NbtCompound nbt = buf.readNbt();
        return new ConfigSyncS2CPayload(t, nbt);
    }
}

