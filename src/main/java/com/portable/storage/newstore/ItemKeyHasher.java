package com.portable.storage.newstore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

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
            // 使用 ItemStack.CODEC，序列化完整的物品属性（包括所有组件）
            // 通过 RegistryOps 确保跨世界/数据包环境一致
            var ops = (lookup != null) ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
            // 使用完整的ItemStack进行序列化，确保所有组件都被包含
            ItemStack normalizedStack = stack.copy();
            normalizedStack.setCount(1); // 只归一化数量，保留所有其他组件
            var enc = ItemStack.CODEC.encodeStart(ops, normalizedStack);
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

}


