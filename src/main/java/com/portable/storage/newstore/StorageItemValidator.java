package com.portable.storage.newstore;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;

/**
 * 存储物品的校验工具，确保入库物品可以被完整序列化/反序列化。
 */
public final class StorageItemValidator {
    private StorageItemValidator() {}

    public static ValidationResult validate(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (stack == null || stack.isEmpty()) {
            return ValidationResult.invalid("empty_stack");
        }
        try {
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            var ops = (lookup != null)
                ? net.minecraft.registry.RegistryOps.of(NbtOps.INSTANCE, lookup)
                : NbtOps.INSTANCE;
            var encoded = ItemStack.CODEC.encodeStart(ops, normalized);
            if (encoded.result().isEmpty()) {
                return ValidationResult.invalid("encode_failed");
            }
            var decoded = ItemStack.CODEC.parse(ops, encoded.result().get());
            if (decoded.result().isEmpty() || decoded.result().get().isEmpty()) {
                return ValidationResult.invalid("decode_failed");
            }
            return ValidationResult.success();
        } catch (Exception e) {
            return ValidationResult.invalid("exception:" + e.getClass().getSimpleName());
        }
    }

    public record ValidationResult(boolean valid, String reason) {
        private static final ValidationResult VALID_INSTANCE = new ValidationResult(true, "");

        public static ValidationResult success() {
            return VALID_INSTANCE;
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason == null ? "unknown" : reason);
        }
    }
}

