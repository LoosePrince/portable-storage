package com.portablestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseAmmoBridge;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.util.FakePlayerUtils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

@Mixin(BowItem.class)
public abstract class BowItemMixin {

    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void onStoppedUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks,
            CallbackInfoReturnable<Boolean> ci) {
        if (level.isClientSide())
            return;
        if (!(user instanceof ServerPlayer player))
            return;
        if (FakePlayerUtils.isFakePlayer(player))
            return;
        if (!Boolean.TRUE.equals(ci.getReturnValue())) {
            WarehouseAmmoBridge.clear(player, stack);
            return;
        }
        if (player.getAbilities().instabuild)
            return;

        ItemStack ammoTemplate = WarehouseAmmoBridge.consume(player, stack);
        if (!isArrow(ammoTemplate))
            return;

        PlayerWarehouse warehouse = WarehouseService.get(player);
        if (warehouse == null || !warehouse.isEnabled())
            return;

        boolean hasInfinity = hasInfinityEnchantment(stack);
        if (hasInfinity && ammoTemplate.is(Items.ARROW)) {
            return;
        }

        WarehouseService.commitIfWarehouseChanged(player, warehouse, "bow_ammo.consume", () -> {
            ItemStack taken = WarehouseManager.takeMatching(warehouse, ammoTemplate, 1, true);
            return !taken.isEmpty();
        });
    }

    private boolean hasInfinityEnchantment(ItemStack stack) {
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (var entry : enchantments.entrySet()) {
                net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = entry.getKey();
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

    private boolean isArrow(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW));
    }
}