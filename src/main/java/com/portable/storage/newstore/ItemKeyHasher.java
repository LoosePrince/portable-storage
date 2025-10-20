package com.portable.storage.newstore;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 物品唯一Key生成：包含物品ID + Mojang DataComponent 序列化（稳定）后做 SHA-256。
 */
public final class ItemKeyHasher {
    private ItemKeyHasher() {}

    public static String hash(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (stack == null || stack.isEmpty()) {
            return ""; // 空栈返回空key，调用侧需要自行判断
        }

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");

            // 1) 物品ID（必须包含）
            Item item = stack.getItem();
            Identifier id = Registries.ITEM.getId(item);
            if (id != null) {
                sha.update(id.toString().getBytes(StandardCharsets.UTF_8));
            }

            // 2) DataComponent 完整稳定序列化
            // 使用 ItemStack.CODEC，仅序列化“无数量影响”的物品属性（按 Mojang 定义）
            // 通过 RegistryOps 确保跨世界/数据包环境一致
            var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
            var enc = ItemStack.CODEC.encodeStart(ops, normalizeCount(stack));
            enc.result().ifPresent(nbt -> {
                // 直接以 NBT 的规范字节序列做哈希；这里转回字符串再入哈希，足够稳定
                String canonical = nbt.toString();
                sha.update(canonical.getBytes(StandardCharsets.UTF_8));
            });

            return HexFormat.of().formatHex(sha.digest());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 归一化计数为1，避免数量进入哈希；附带把空/默认组件统一去除差异。
     */
    private static ItemStack normalizeCount(ItemStack in) {
        ItemStack copy = in.copy();
        copy.setCount(1);
        // 对部分常见组件做轻度归一（若为空则移除）
        NbtComponent custom = copy.get(DataComponentTypes.CUSTOM_DATA);
        if (custom != null && (custom.copyNbt() == null || custom.copyNbt().isEmpty())) {
            copy.remove(DataComponentTypes.CUSTOM_DATA);
        }
        NbtComponent be = copy.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (be != null && (be.copyNbt() == null || be.copyNbt().isEmpty())) {
            copy.remove(DataComponentTypes.BLOCK_ENTITY_DATA);
        }
        return copy;
    }
}


