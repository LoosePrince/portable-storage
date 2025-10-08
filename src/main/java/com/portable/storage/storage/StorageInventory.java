package com.portable.storage.storage;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.Registries;

import java.util.*;

/**
 * 随身仓库存储（无限堆叠 + 无固定槽位）：
 * - 以“物品+组件”变体为单位进行堆叠，保留 NBT/组件（如自定义数据、名称、附魔等）
 * - 根据排序规则生成“视图索引”，前端以索引访问；不存储格子位置
 */
public class StorageInventory {
    private static final class Entry {
        ItemStack template; // 仅作为此变体的展示/比对，不代表实际总数
        long count;
        long updatedAt;
    }

    private final List<Entry> variants = new ArrayList<>();
    private List<Integer> sortedIndices = null; // 缓存视图顺序（索引到 variants）

    public StorageInventory(int ignoredCapacity) { }

    private static boolean stacksEqual(ItemStack a, ItemStack b) {
        return ItemStack.areItemsAndComponentsEqual(a, b);
    }

    private void markDirty() { sortedIndices = null; }

    public int getCapacity() { return variants.size(); }

    private List<Integer> getSortedIndices() {
        if (sortedIndices == null) {
            List<Integer> idx = new ArrayList<>(variants.size());
            for (int i = 0; i < variants.size(); i++) idx.add(i);
            // 数量降序，其次最近更新时间，再其次按物品ID
            idx.sort((ia, ib) -> {
                Entry a = variants.get(ia);
                Entry b = variants.get(ib);
                int c = Long.compare(b.count, a.count);
                if (c != 0) return c;
                c = Long.compare(b.updatedAt, a.updatedAt);
                if (c != 0) return c;
                return Registries.ITEM.getId(a.template.getItem()).compareTo(Registries.ITEM.getId(b.template.getItem()));
            });
            sortedIndices = idx;
        }
        return sortedIndices;
    }

    private Entry getEntryByViewIndex(int index) {
        List<Integer> idx = getSortedIndices();
        if (index < 0 || index >= idx.size()) return null;
        return variants.get(idx.get(index));
    }

    public long getCountByIndex(int index) {
        Entry e = getEntryByViewIndex(index);
        return e == null ? 0L : e.count;
    }

    public ItemStack getDisplayStack(int index) {
        Entry e = getEntryByViewIndex(index);
        if (e == null || e.count <= 0 || e.template.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = e.template.copy();
        s.setCount((int)Math.min(Math.max(1, s.getMaxCount()), e.count));
        return s;
    }

    public long getTimestampByIndex(int index) {
        Entry e = getEntryByViewIndex(index);
        return e != null ? e.updatedAt : 0L;
    }

    public long takeByIndex(int index, long want, long ts) {
        if (want <= 0) return 0;
        Entry e = getEntryByViewIndex(index);
        if (e == null || e.count <= 0) return 0;
        long got = Math.min(e.count, want);
        e.count -= got;
        e.updatedAt = ts;
        if (e.count == 0) {
            variants.remove(e);
        }
        markDirty();
        return got;
    }

    /**
     * 向仓库插入完整的 ItemStack（保留组件）。返回剩余无法存入的部分。
     */
    public ItemStack insertItemStack(ItemStack stack, long ts) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        // 查找同变体
        for (Entry e : variants) {
            if (stacksEqual(e.template, stack)) {
                addToEntry(e, stack.getCount(), ts);
                return ItemStack.EMPTY;
            }
        }
        // 新建变体
        Entry e = new Entry();
        e.template = stack.copy();
        e.template.setCount(1);
        e.count = 0;
        e.updatedAt = ts;
        variants.add(e);
        addToEntry(e, stack.getCount(), ts);
        markDirty();
        return ItemStack.EMPTY;
    }

    /**
     * 向仓库插入物品，保留原有的时间戳（仅用于合并快照）。
     */
    public void insertItemStackWithOriginalTimestamp(ItemStack stack, long originalTimestamp) {
        if (stack == null || stack.isEmpty()) return;
        // 查找同变体
        for (Entry e : variants) {
            if (stacksEqual(e.template, stack)) {
                // 合并数量，但不更新时间戳
                try {
                    e.count = Math.addExact(e.count, stack.getCount());
                } catch (ArithmeticException ex) {
                    e.count = Long.MAX_VALUE;
                }
                markDirty();
                return;
            }
        }
        // 新建变体，使用原来的时间戳
        Entry e = new Entry();
        e.template = stack.copy();
        e.template.setCount(1);
        e.count = stack.getCount();
        e.updatedAt = originalTimestamp;
        variants.add(e);
        markDirty();
    }

    private void addToEntry(Entry e, long add, long ts) {
        if (add <= 0) return;
        try {
            e.count = Math.addExact(e.count, add);
        } catch (ArithmeticException ex) {
            e.count = Long.MAX_VALUE;
        }
        e.updatedAt = ts;
        markDirty();
    }

    public boolean isEmpty() { return variants.isEmpty(); }

    public void clear() { variants.clear(); markDirty(); }

    public NbtCompound writeNbt(NbtCompound out) {
        return writeNbt(out, null);
    }

    public NbtCompound writeNbt(NbtCompound out, RegistryWrapper.WrapperLookup lookup) {
        final DynamicOps<net.minecraft.nbt.NbtElement> ops =
            (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
        NbtList list = new NbtList();
        for (int vi : getSortedIndices()) {
            Entry e = variants.get(vi);
            if (e == null || e.count <= 0 || e.template.isEmpty()) continue;
            NbtCompound c = new NbtCompound();
            // 写入完整 ItemStack（含全部组件/NBT）
            var encoded = ItemStack.CODEC.encodeStart(ops, e.template);
            encoded.result().ifPresent(nbtElement -> c.put("item_full", nbtElement));
            // 兼容旧版：保留精简字段，便于向后兼容（读取时将优先 item_full）
            {
                NbtCompound itemTag = new NbtCompound();
                itemTag.putString("id", Registries.ITEM.getId(e.template.getItem()).toString());
                var custom = e.template.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                if (custom != null) {
                    itemTag.put("custom", custom.copyNbt());
                }
                var be = e.template.get(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA);
                if (be != null) {
                    itemTag.put("block_entity", be.copyNbt());
                }
                Boolean glint = e.template.get(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
                if (glint != null && glint) {
                    itemTag.putBoolean("glint", true);
                }
                c.put("item", itemTag);
            }
            c.putLong("count", e.count);
            c.putLong("ts", e.updatedAt);
            list.add(c);
        }
        out.put("entries", list);
        out.putInt("capacity", list.size());
        return out;
    }

    public void readNbt(NbtCompound in) {
        readNbt(in, null);
    }

    public void readNbt(NbtCompound in, RegistryWrapper.WrapperLookup lookup) {
        clear();
        if (in == null) return;
        if (!in.contains("entries", NbtElement.LIST_TYPE)) return;
        NbtList list = in.getList("entries", NbtElement.COMPOUND_TYPE);
        final DynamicOps<net.minecraft.nbt.NbtElement> ops =
            (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            ItemStack template = ItemStack.EMPTY;
            // 优先读取完整模板
            if (c.contains("item_full")) {
                var parse = ItemStack.CODEC.parse(ops, c.get("item_full"));
                template = parse.result().orElse(ItemStack.EMPTY);
            }
            // 兼容旧字段
            if (template.isEmpty() && c.contains("item", NbtElement.COMPOUND_TYPE)) {
                NbtCompound itemTag = c.getCompound("item");
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(itemTag.getString("id"));
                if (id != null) {
                    net.minecraft.item.Item item = Registries.ITEM.get(id);
                    if (item != null && item != net.minecraft.item.Items.AIR) {
                        template = new ItemStack(item);
                        if (itemTag.contains("custom", NbtElement.COMPOUND_TYPE)) {
                            template.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(itemTag.getCompound("custom")));
                        }
                        if (itemTag.contains("block_entity", NbtElement.COMPOUND_TYPE)) {
                            template.set(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA, net.minecraft.component.type.NbtComponent.of(itemTag.getCompound("block_entity")));
                        }
                        if (itemTag.contains("glint", NbtElement.BYTE_TYPE) && itemTag.getBoolean("glint")) {
                            template.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                        }
                    }
                }
            }
            if (template.isEmpty()) continue;
            Entry e = new Entry();
            e.template = template;
            e.count = Math.max(0, c.getLong("count"));
            e.updatedAt = c.contains("ts") ? c.getLong("ts") : 0L;
            if (e.count > 0) variants.add(e);
        }
        markDirty();
    }
}
