package com.portablestorage.network;

import com.portablestorage.PortableStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import java.util.Optional;

public record C2SUpdateWarehouseStatePayload(
    Optional<Integer> scrollDelta,
    Optional<String> searchText,
    Optional<Integer> settingId,
    Optional<Integer> settingValue,
    Optional<Integer> rowsDelta,
    Optional<Integer> upgradeScrollDelta
) implements FabricPacket {
    public static final PacketType<C2SUpdateWarehouseStatePayload> TYPE = PacketType.create(
        PortableStorage.id("update_state"), (buf) -> {
            return new C2SUpdateWarehouseStatePayload(buf);
        }
    );

    public C2SUpdateWarehouseStatePayload(FriendlyByteBuf buf) {
        this(
            buf.readOptional(FriendlyByteBuf::readInt),
            buf.readOptional(buf2 -> buf2.readUtf(32767)),
            buf.readOptional(FriendlyByteBuf::readInt),
            buf.readOptional(FriendlyByteBuf::readInt),
            buf.readOptional(FriendlyByteBuf::readInt),
            buf.readOptional(FriendlyByteBuf::readInt)
        );
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeOptional(scrollDelta, FriendlyByteBuf::writeInt);
        buf.writeOptional(searchText, FriendlyByteBuf::writeUtf);
        buf.writeOptional(settingId, FriendlyByteBuf::writeInt);
        buf.writeOptional(settingValue, FriendlyByteBuf::writeInt);
        buf.writeOptional(rowsDelta, FriendlyByteBuf::writeInt);
        buf.writeOptional(upgradeScrollDelta, FriendlyByteBuf::writeInt);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}

