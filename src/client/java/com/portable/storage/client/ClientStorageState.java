package com.portable.storage.client;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.DefaultedList;

public final class ClientStorageState {
    private static DefaultedList<ItemStack> display = DefaultedList.ofSize(54, ItemStack.EMPTY);
    private static long[] counts = new long[54];
    private static long[] timestamps = new long[54];
    private static int capacity = 54;
    private static boolean storageEnabled = false; // 默认未启用，等待服务端同步

    private ClientStorageState() {}

    public static DefaultedList<ItemStack> getStacks() { 
        // 优先使用增量同步状态，如果没有数据则使用传统状态
        if (IncrementalStorageState.hasData()) {
            return IncrementalStorageState.getDisplayList();
        }
        return display; 
    }
    public static int getCapacity() { 
        if (IncrementalStorageState.hasData()) {
            return IncrementalStorageState.getCapacity();
        }
        return capacity; 
    }
    public static long getCount(int index) { 
        if (IncrementalStorageState.hasData()) {
            return IncrementalStorageState.getCount(index);
        }
        return (index>=0 && index<counts.length) ? counts[index] : 0L; 
    }
    public static long getTimestamp(int index) { 
        if (IncrementalStorageState.hasData()) {
            return IncrementalStorageState.getTimestamp(index);
        }
        return (index>=0 && index<timestamps.length) ? timestamps[index] : 0L; 
    }
    public static boolean isStorageEnabled() { return storageEnabled; }
    
    public static void setStorageEnabled(boolean enabled) {
        storageEnabled = enabled;
    }

    public static void updateFromNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        int cap = nbt.getInt("capacity");
        if (cap <= 0) cap = 54;
        capacity = cap;
        display = DefaultedList.ofSize(capacity, ItemStack.EMPTY);
        counts = new long[capacity];
        timestamps = new long[capacity];
        if (!nbt.contains("entries", NbtElement.LIST_TYPE)) return;
        NbtList list = nbt.getList("entries", NbtElement.COMPOUND_TYPE);
        int idx = 0;
        for (int i = 0; i < list.size() && idx < capacity; i++) {
            NbtCompound e = list.getCompound(i);
            ItemStack stack = ItemStack.EMPTY;
            // 优先完整模板
            if (e.contains("item_full")) {
                final DynamicOps<net.minecraft.nbt.NbtElement> ops =
                    (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
                var parse = ItemStack.CODEC.parse(ops, e.get("item_full"));
                stack = parse.result().orElse(ItemStack.EMPTY);
            }
            // 兼容旧字段
            if (stack.isEmpty() && e.contains("item", NbtElement.COMPOUND_TYPE)) {
                NbtCompound itemTag = e.getCompound("item");
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
            }
            long count = Math.max(0, e.getLong("count"));
            long timestamp = e.contains("ts") ? e.getLong("ts") : 0L;
            if (stack.isEmpty() || count <= 0) continue;
            counts[idx] = count;
            timestamps[idx] = timestamp;
            stack.setCount((int)Math.min(stack.getMaxCount(), count));
            display.set(idx, stack);
            idx++;
        }
    }
}


