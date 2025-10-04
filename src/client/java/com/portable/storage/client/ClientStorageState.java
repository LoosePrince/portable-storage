package com.portable.storage.client;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.DefaultedList;

public final class ClientStorageState {
    private static DefaultedList<ItemStack> display = DefaultedList.ofSize(54, ItemStack.EMPTY);
    private static long[] counts = new long[54];
    private static int capacity = 54;

    private ClientStorageState() {}

    public static DefaultedList<ItemStack> getStacks() { return display; }
    public static int getCapacity() { return capacity; }
    public static long getCount(int index) { return (index>=0 && index<counts.length) ? counts[index] : 0L; }

    public static void updateFromNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        int cap = nbt.getInt("capacity");
        if (cap <= 0) cap = 54;
        capacity = cap;
        display = DefaultedList.ofSize(capacity, ItemStack.EMPTY);
        counts = new long[capacity];
        if (!nbt.contains("entries", NbtElement.LIST_TYPE)) return;
        NbtList list = nbt.getList("entries", NbtElement.COMPOUND_TYPE);
        int idx = 0;
        for (int i = 0; i < list.size() && idx < capacity; i++) {
            NbtCompound e = list.getCompound(i);
            if (!e.contains("item", NbtElement.COMPOUND_TYPE)) continue;
            NbtCompound itemTag = e.getCompound("item");
            ItemStack stack = ItemStack.EMPTY;
            if (itemTag.contains("id")) {
                var id = net.minecraft.util.Identifier.tryParse(itemTag.getString("id"));
                if (id != null) {
                    Item it = Registries.ITEM.get(id);
                    if (it != null && it != net.minecraft.item.Items.AIR) {
                        stack = new ItemStack(it);
                        if (itemTag.contains("custom", NbtElement.COMPOUND_TYPE)) {
                            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(itemTag.getCompound("custom")));
                        }
                        if (itemTag.contains("block_entity", NbtElement.COMPOUND_TYPE)) {
                            stack.set(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA, net.minecraft.component.type.NbtComponent.of(itemTag.getCompound("block_entity")));
                        }
                        if (itemTag.contains("glint", NbtElement.BYTE_TYPE) && itemTag.getBoolean("glint")) {
                            stack.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                        }
                    }
                }
            }
            long count = Math.max(0, e.getLong("count"));
            if (stack.isEmpty() || count <= 0) continue;
            counts[idx] = count;
            stack.setCount((int)Math.min(stack.getMaxCount(), count));
            display.set(idx, stack);
            idx++;
        }
    }
}


