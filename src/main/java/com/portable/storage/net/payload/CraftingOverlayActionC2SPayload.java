package com.portable.storage.net.payload;

import com.portable.storage.PortableStorage;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 统一的覆盖式合成/配方填充/返还 载荷。
 */
public final class CraftingOverlayActionC2SPayload {
    public static final Identifier ID = new Identifier(PortableStorage.MOD_ID, "crafting_overlay_action");

    public enum Action { CLICK, DOUBLE_CLICK, REFILL, EMI_FILL, REFUND }

    private final Action action;
    private final int slotIndex;
    private final int button;
    private final boolean shift;
    private final ItemStack targetStack;
    private final String recipeId;
    private final int[] slotIndices;
    private final int[] itemCounts;

    public CraftingOverlayActionC2SPayload(Action action, int slotIndex, int button, boolean shift, ItemStack targetStack, String recipeId, int[] slotIndices, int[] itemCounts) {
        this.action = action;
        this.slotIndex = slotIndex;
        this.button = button;
        this.shift = shift;
        this.targetStack = targetStack == null ? ItemStack.EMPTY : targetStack;
        this.recipeId = recipeId == null ? "" : recipeId;
        this.slotIndices = slotIndices;
        this.itemCounts = itemCounts;
    }

    public Action action() { return action; }
    public int slotIndex() { return slotIndex; }
    public int button() { return button; }
    public boolean shift() { return shift; }
    public ItemStack targetStack() { return targetStack; }
    public String recipeId() { return recipeId; }
    public int[] slotIndices() { return slotIndices; }
    public int[] itemCounts() { return itemCounts; }

    public static void write(PacketByteBuf buf, CraftingOverlayActionC2SPayload value) {
        buf.writeVarInt(value.action.ordinal());
        switch (value.action) {
            case CLICK -> {
                buf.writeVarInt(value.slotIndex);
                buf.writeVarInt(value.button);
                buf.writeBoolean(value.shift);
            }
            case DOUBLE_CLICK -> { }
            case REFILL -> {
                buf.writeVarInt(value.slotIndex);
                buf.writeItemStack(value.targetStack);
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
            case REFUND -> { }
        }
    }

    public static CraftingOverlayActionC2SPayload read(PacketByteBuf buf) {
        Action action = Action.values()[buf.readVarInt()];
        switch (action) {
            case CLICK:
                return new CraftingOverlayActionC2SPayload(action, buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), ItemStack.EMPTY, "", null, null);
            case DOUBLE_CLICK:
                return new CraftingOverlayActionC2SPayload(action, 0, 0, false, ItemStack.EMPTY, "", null, null);
            case REFILL:
                return new CraftingOverlayActionC2SPayload(action, buf.readVarInt(), 0, false, buf.readItemStack(), "", null, null);
            case EMI_FILL:
                String rid = buf.readString();
                int n1 = buf.readVarInt();
                int[] slots = new int[Math.max(0, n1)];
                for (int i = 0; i < slots.length; i++) slots[i] = buf.readVarInt();
                int n2 = buf.readVarInt();
                int[] counts = new int[Math.max(0, n2)];
                for (int i = 0; i < counts.length; i++) counts[i] = buf.readVarInt();
                return new CraftingOverlayActionC2SPayload(action, 0, 0, false, ItemStack.EMPTY, rid, slots, counts);
            case REFUND:
                return new CraftingOverlayActionC2SPayload(action, 0, 0, false, ItemStack.EMPTY, "", null, null);
            default:
                return new CraftingOverlayActionC2SPayload(Action.DOUBLE_CLICK, 0, 0, false, ItemStack.EMPTY, "", null, null);
        }
    }
}

