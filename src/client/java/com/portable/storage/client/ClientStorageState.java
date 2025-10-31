package com.portable.storage.client;

import com.mojang.serialization.DynamicOps;
import com.portable.storage.net.payload.SyncControlC2SPayload;
import com.portable.storage.storage.StorageType;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.DefaultedList;

public final class ClientStorageState {
    private static DefaultedList<ItemStack> display = DefaultedList.ofSize(54, ItemStack.EMPTY);
    private static long[] counts = new long[54];
    private static long[] timestamps = new long[54];
    private static int capacity = 54;
    private static boolean storageEnabled = false; // 默认未启用，等待服务端同步
    private static StorageType storageType = StorageType.FULL; // 默认完整仓库
    // 增量会话与序列
    private static long clientSessionId = 0L;
    private static int expectedSeq = 1;

    private ClientStorageState() {}

    public static DefaultedList<ItemStack> getStacks() { 
        return display; 
    }
    public static int getCapacity() { 
        return capacity; 
    }
    public static long getCount(int index) { 
        return (index>=0 && index<counts.length) ? counts[index] : 0L; 
    }
    public static long getTimestamp(int index) { 
        return (index>=0 && index<timestamps.length) ? timestamps[index] : 0L; 
    }
    public static boolean isStorageEnabled() { return storageEnabled; }
    
    public static void setStorageEnabled(boolean enabled) {
        storageEnabled = enabled;
    }
    
    public static StorageType getStorageType() { return storageType; }
    
    public static void setStorageType(StorageType type) {
        storageType = type;
    }

    public static void updateFromNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        // 可兼容读取 sessionId 以重置序列
        if (nbt.contains("sessionId")) {
            clientSessionId = nbt.getLong("sessionId");
            expectedSeq = 1;
        }
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

    public static void applyDiff(long sessionId, int seq, NbtCompound diff) {
        if (sessionId != clientSessionId || seq != expectedSeq) {
            // 会话或序号不匹配：请求全量回退
            try {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new SyncControlC2SPayload(
                        SyncControlC2SPayload.Op.REQUEST,
                        0L,
                        false
                    )
                );
            } catch (Throwable ignored) {}
            return;
        }
        if (diff == null) {
            expectedSeq++;
            return;
        }
        // removes
        if (diff.contains("removes", NbtElement.LIST_TYPE)) {
            NbtList rm = diff.getList("removes", NbtElement.STRING_TYPE);
            for (int i = 0; i < rm.size(); i++) {
                String key = rm.getString(i);
                removeByKey(key);
            }
        }
        // upserts
        if (diff.contains("upserts", NbtElement.LIST_TYPE)) {
            NbtList up = diff.getList("upserts", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < up.size(); i++) {
                NbtCompound e = up.getCompound(i);
                upsertEntry(e, lookupForClient());
            }
        }
        expectedSeq++;
    }

    private static void removeByKey(String key) {
        // 简化实现：线性扫描并清理匹配项；后续可改为 Map
        for (int i = 0; i < capacity; i++) {
            ItemStack s = display.get(i);
            if (s.isEmpty()) continue;
            if (key.equals(makeKeyForStack(s))) {
                display.set(i, ItemStack.EMPTY);
                counts[i] = 0L;
                timestamps[i] = 0L;
            }
        }
    }

    private static void upsertEntry(NbtCompound e, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        if (e == null) return;
        String key = e.getString("key");
        long count = Math.max(0, e.getLong("count"));
        long ts = e.contains("ts") ? e.getLong("ts") : 0L;
        ItemStack stack = ItemStack.EMPTY;
        if (e.contains("display")) {
            final DynamicOps<net.minecraft.nbt.NbtElement> ops = (lookup != null)
                ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup)
                : NbtOps.INSTANCE;
            var parse = ItemStack.CODEC.parse(ops, e.get("display"));
            stack = parse.result().orElse(ItemStack.EMPTY);
        }
        if (stack.isEmpty() || count <= 0) {
            removeByKey(key);
            return;
        }
        // 查找已有槽位就地更新，否则放入首个空位（简化版）
        int idx = findIndexByKey(key);
        if (idx < 0) idx = findFirstEmpty();
        if (idx < 0) return; // 已满，后续可扩容或触发全量
        counts[idx] = count;
        timestamps[idx] = ts;
        stack.setCount((int)Math.min(stack.getMaxCount(), count));
        display.set(idx, stack);
    }

    private static int findIndexByKey(String key) {
        for (int i = 0; i < capacity; i++) {
            ItemStack s = display.get(i);
            if (s.isEmpty()) continue;
            if (key.equals(makeKeyForStack(s))) return i;
        }
        return -1;
    }

    private static int findFirstEmpty() {
        for (int i = 0; i < capacity; i++) if (display.get(i).isEmpty()) return i;
        return -1;
    }

    private static String makeKeyForStack(ItemStack s) {
        // 直接使用 ItemKeyHasher 生成键，确保与新版储存系统完全一致
        return com.portable.storage.newstore.ItemKeyHasher.hash(s, lookupForClient());
    }

    private static net.minecraft.registry.RegistryWrapper.WrapperLookup lookupForClient() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        return (mc != null && mc.player != null) ? mc.player.getRegistryManager() : null;
    }
}


