package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {

    @Inject(method = "releaseUsing", at = @At("TAIL"))
    private void onStoppedUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (level.isClientSide) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (player.getAbilities().instabuild) return;

        // 检查弩是否已装填
        ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (chargedProjectiles == null || chargedProjectiles.isEmpty()) return;

        // 优先消耗背包中的弹药（原版逻辑会处理）
        if (hasAnyAmmo(player)) return;

        PlayerWarehouse warehouse = ModComponents.getWarehouse(player.getServer(), player.getUUID());
        if (warehouse == null || !warehouse.isEnabled()) return;

        // 查找合适的弹药
        ItemStack matchedAmmo = null;
        for (var entry : warehouse.getStorageList()) {
            ItemStack s = entry.getItemStack();
            if (isAmmo(s)) {
                if (s.is(Items.ARROW)) {
                    matchedAmmo = s;
                    break;
                }
                if (matchedAmmo == null) matchedAmmo = s;
            }
        }

        if (matchedAmmo != null) {
            // 无限附魔仅对普通箭有效，药水箭、光灵箭和烟花火箭即使有无限附魔也会扣除
            boolean hasInfinity = hasInfinityEnchantment(stack);
            if (hasInfinity && matchedAmmo.is(Items.ARROW)) {
                return; // 有无限附魔且是普通箭，不扣除
            }
            WarehouseManager.takeMatching(warehouse, matchedAmmo, 1, true);
        }
    }

    private boolean hasInfinityEnchantment(ItemStack stack) {
        // 检查弩是否有无限附魔
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = entry.getKey();
                // 使用 unwrapKey() 获取 ResourceKey 进行比较
                var enchantmentKeyOpt = holder.unwrapKey();
                if (enchantmentKeyOpt.isPresent()) {
                    var enchantmentKey = enchantmentKeyOpt.get();
                    if (enchantmentKey.equals(Enchantments.INFINITY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAnyAmmo(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isAmmo(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private boolean isAmmo(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.FIREWORK_ROCKET));
    }
}

