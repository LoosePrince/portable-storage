package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 容器显示配置同步数据包（服务端到客户端）
 * 用于将服务器配置的容器显示设置同步到客户端
 */
public record ContainerDisplayConfigSyncS2CPayload(
    boolean stonecutter,
    boolean cartographyTable,
    boolean smithingTable,
    boolean grindstone,
    boolean loom,
    boolean furnace,
    boolean smoker,
    boolean blastFurnace,
    boolean anvil,
    boolean enchantingTable,
    boolean brewingStand,
    boolean beacon,
    boolean chest,
    boolean barrel,
    boolean enderChest,
    boolean shulkerBox,
    boolean dispenser,
    boolean dropper,
    boolean crafter,
    boolean hopper,
    boolean trappedChest,
    boolean hopperMinecart,
    boolean chestMinecart,
    boolean chestBoat,
    boolean bambooChestRaft
) implements CustomPayload {
    
    public static final Id<ContainerDisplayConfigSyncS2CPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "container_display_config"));
    public static final PacketCodec<RegistryByteBuf, ContainerDisplayConfigSyncS2CPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            buf.writeBoolean(payload.stonecutter());
            buf.writeBoolean(payload.cartographyTable());
            buf.writeBoolean(payload.smithingTable());
            buf.writeBoolean(payload.grindstone());
            buf.writeBoolean(payload.loom());
            buf.writeBoolean(payload.furnace());
            buf.writeBoolean(payload.smoker());
            buf.writeBoolean(payload.blastFurnace());
            buf.writeBoolean(payload.anvil());
            buf.writeBoolean(payload.enchantingTable());
            buf.writeBoolean(payload.brewingStand());
            buf.writeBoolean(payload.beacon());
            buf.writeBoolean(payload.chest());
            buf.writeBoolean(payload.barrel());
            buf.writeBoolean(payload.enderChest());
            buf.writeBoolean(payload.shulkerBox());
            buf.writeBoolean(payload.dispenser());
            buf.writeBoolean(payload.dropper());
            buf.writeBoolean(payload.crafter());
            buf.writeBoolean(payload.hopper());
            buf.writeBoolean(payload.trappedChest());
            buf.writeBoolean(payload.hopperMinecart());
            buf.writeBoolean(payload.chestMinecart());
            buf.writeBoolean(payload.chestBoat());
            buf.writeBoolean(payload.bambooChestRaft());
        },
        buf -> new ContainerDisplayConfigSyncS2CPayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean()
        )
    );
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
