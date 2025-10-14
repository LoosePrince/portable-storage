package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 统一的覆盖式合成/配方填充/返还 载荷。
 */
public record CraftingOverlayActionC2SPayload(
    Action action,
    int slotIndex,
    int button,
    boolean shift,
    ItemStack targetStack,
    String recipeId,
    int[] slotIndices,
    int[] itemCounts
) implements CustomPayload {
    public static final Id<CraftingOverlayActionC2SPayload> ID = new Id<>(Identifier.of(PortableStorage.MOD_ID, "crafting_overlay_action"));

    public static final PacketCodec<RegistryByteBuf, CraftingOverlayActionC2SPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.action.ordinal());
            switch (value.action) {
                case CLICK -> {
                    buf.writeVarInt(value.slotIndex);
                    buf.writeVarInt(value.button);
                    buf.writeBoolean(value.shift);
                }
                case DOUBLE_CLICK -> {
                    // 无附加数据
                }
                case REFILL -> {
                    buf.writeVarInt(value.slotIndex);
                    ItemStack.PACKET_CODEC.encode(buf, value.targetStack);
                }
                case EMI_FILL -> {
                    buf.writeString(value.recipeId != null ? value.recipeId : "");
                    int[] slots = value.slotIndices != null ? value.slotIndices : new int[0];
                    int[] counts = value.itemCounts != null ? value.itemCounts : new int[0];
                    buf.writeVarInt(slots.length);
                    for (int s : slots) buf.writeVarInt(s);
                    buf.writeVarInt(counts.length);
                    for (int c : counts) buf.writeVarInt(c);
                }
                case REFUND -> {
                    // 无附加数据
                }
            }
        },
        buf -> {
            Action action = Action.values()[buf.readVarInt()];
            return switch (action) {
                case CLICK -> new CraftingOverlayActionC2SPayload(action, buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), ItemStack.EMPTY, "", null, null);
                case DOUBLE_CLICK -> new CraftingOverlayActionC2SPayload(action, 0, 0, false, ItemStack.EMPTY, "", null, null);
                case REFILL -> new CraftingOverlayActionC2SPayload(action, buf.readVarInt(), 0, false, ItemStack.PACKET_CODEC.decode(buf), "", null, null);
                case EMI_FILL -> {
                    String rid = buf.readString();
                    int n1 = buf.readVarInt();
                    int[] slots = new int[Math.max(0, n1)];
                    for (int i = 0; i < slots.length; i++) slots[i] = buf.readVarInt();
                    int n2 = buf.readVarInt();
                    int[] counts = new int[Math.max(0, n2)];
                    for (int i = 0; i < counts.length; i++) counts[i] = buf.readVarInt();
                    yield new CraftingOverlayActionC2SPayload(action, 0, 0, false, ItemStack.EMPTY, rid, slots, counts);
                }
                case REFUND -> new CraftingOverlayActionC2SPayload(action, 0, 0, false, ItemStack.EMPTY, "", null, null);
            };
        }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum Action { CLICK, DOUBLE_CLICK, REFILL, EMI_FILL, REFUND }
}


