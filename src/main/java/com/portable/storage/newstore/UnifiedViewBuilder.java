package com.portable.storage.newstore;

import com.portable.storage.storage.StorageInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;

import java.util.*;

/**
 * 将旧版库存（StorageInventory entries）与新版玩家存储（PlayerStore entries）合并为下发用 entries。
 */
public final class UnifiedViewBuilder {
    private UnifiedViewBuilder() {}

    public static NbtCompound build(StorageInventory legacy, Map<String, PlayerStore.Entry> newEntries,
                                    TemplateIndex index, RegistryWrapper.WrapperLookup lookup,
                                    TemplateSlices.MinecraftServerLike server) {
        NbtCompound out = new NbtCompound();
        NbtList list = new NbtList();

        // 1) 先写入旧版 entries（保持原样显示）
        if (legacy != null) {
            NbtCompound tmp = new NbtCompound();
            legacy.writeNbt(tmp, lookup);
            if (tmp.contains("entries")) {
                NbtList oldList = tmp.getList("entries", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < oldList.size(); i++) {
                    list.add(oldList.getCompound(i));
                }
            }
        }

        // 2) 新版 entries：用模板回填 item_full
        if (newEntries != null && !newEntries.isEmpty()) {
            for (PlayerStore.Entry e : newEntries.values()) {
                if (e.count <= 0) continue;
                ItemStack stack = TemplateSlices.getTemplate(server, index, e.key, lookup);
                if (stack.isEmpty()) continue;
                NbtCompound c = new NbtCompound();
                var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
                var enc = ItemStack.CODEC.encodeStart(ops, stack);
                enc.result().ifPresent(nbt -> c.put("item_full", nbt));
                c.putLong("count", e.count);
                c.putLong("ts", e.ts);
                list.add(c);
            }
        }

        out.put("entries", list);
        out.putInt("capacity", list.size());
        return out;
    }
}


