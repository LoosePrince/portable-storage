package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 统一的仓库/升级/资源点击与转移动作载荷。
 * 以 action + target 区分具体语义，其余上下文字段按需填充。
 */
public final class StorageActionC2SPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "storage_action");

    public enum Action { CLICK, SHIFT_TAKE, DROP, DEPOSIT_CURSOR, DEPOSIT_SLOT }
    public enum Target { STORAGE, UPGRADE, FLUID, XP_BOTTLE, SLOT, TRASH }

    private final Action action;
    private final Target target;
    private final int index;
    private final int button;
    private final int handlerSlotId;
    private final String resourceType;
    private final int amountType;

    public StorageActionC2SPayload(Action action, Target target, int index, int button, int handlerSlotId, String resourceType, int amountType) {
        this.action = action;
        this.target = target;
        this.index = index;
        this.button = button;
        this.handlerSlotId = handlerSlotId;
        this.resourceType = resourceType;
        this.amountType = amountType;
    }

    public Action action() { return action; }
    public Target target() { return target; }
    public int index() { return index; }
    public int button() { return button; }
    public int handlerSlotId() { return handlerSlotId; }
    public String resourceType() { return resourceType; }
    public int amountType() { return amountType; }

    public static void write(PacketByteBuf buf, StorageActionC2SPayload value) {
        buf.writeVarInt(value.action.ordinal());
        buf.writeVarInt(value.target.ordinal());
        buf.writeVarInt(value.index);
        buf.writeVarInt(value.button);
        buf.writeVarInt(value.handlerSlotId);
        buf.writeString(value.resourceType == null ? "" : value.resourceType);
        buf.writeVarInt(value.amountType);
    }

    public static StorageActionC2SPayload read(PacketByteBuf buf) {
        Action action = Action.values()[buf.readVarInt()];
        Target target = Target.values()[buf.readVarInt()];
        int index = buf.readVarInt();
        int button = buf.readVarInt();
        int handlerSlotId = buf.readVarInt();
        String resourceType = buf.readString();
        int amountType = buf.readVarInt();
        return new StorageActionC2SPayload(action, target, index, button, handlerSlotId, resourceType, amountType);
    }
}


