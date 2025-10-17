package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 统一的仓库/升级/资源点击与转移动作载荷。
 * 以 action + target 区分具体语义，其余上下文字段按需填充。
 */
public record StorageActionC2SPayload(
    Action action,
    Target target,
    int index,
    int button,
    int handlerSlotId,
    String resourceType,
    int amountType
) implements CustomPayload {
    public static final Id<StorageActionC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "storage_action"));

    public static final PacketCodec<RegistryByteBuf, StorageActionC2SPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.action.ordinal());
            buf.writeVarInt(value.target.ordinal());
            buf.writeVarInt(value.index);
            buf.writeVarInt(value.button);
            buf.writeVarInt(value.handlerSlotId);
            buf.writeString(value.resourceType);
            buf.writeVarInt(value.amountType);
        },
        buf -> new StorageActionC2SPayload(
            Action.values()[buf.readVarInt()],
            Target.values()[buf.readVarInt()],
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readString(),
            buf.readVarInt()
        )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum Action {
        CLICK,
        SHIFT_TAKE,
        DROP,
        DEPOSIT_CURSOR,
        DEPOSIT_SLOT
    }

    public enum Target {
        STORAGE,
        UPGRADE,
        FLUID,
        XP_BOTTLE,
        SLOT,
        TRASH
    }
}


