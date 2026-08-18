package com.portablestorage.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.logic.WarehouseAmmoBridge;
import com.portablestorage.util.CompatibilityDebug;
import com.portablestorage.util.FakePlayerUtils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Mixin(Player.class)
public abstract class PlayerProjectileMixin {

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void onGetProjectile(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty())
            return;

        Player player = (Player) (Object) this;
        if (player.level().isClientSide())
            return;
        if (player.getAbilities().instabuild)
            return;
        if (FakePlayerUtils.isFakePlayer(player)) {
            CompatibilityDebug.log("fake-player", () -> "skipped warehouse projectile lookup for " + player.getClass().getName());
            return;
        }

        if (weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem) {
            PlayerWarehouse warehouse = ModComponents.getWarehouse(((ServerLevel) player.level()).getServer(),
                    player.getUUID());
            if (warehouse != null && warehouse.isEnabled()) {
                List<WarehouseEntry> storage = warehouse.getStorageList();
                ItemStack matched = null;

                for (WarehouseEntry entry : storage) {
                    ItemStack stack = entry.getItemStack();
                    if (weapon.getItem() instanceof BowItem && isArrow(stack)) {
                        if (stack.is(Items.ARROW)) {
                            matched = stack.copyWithCount(1);
                            break;
                        }
                        if (matched == null)
                            matched = stack.copyWithCount(1);
                    } else if (weapon.getItem() instanceof CrossbowItem && isCrossbowAmmo(stack)) {
                        if (stack.is(Items.ARROW)) {
                            matched = stack.copyWithCount(1);
                            break;
                        }
                        if (matched == null)
                            matched = stack.copyWithCount(1);
                    }
                }

                if (matched != null) {
                    cir.setReturnValue(matched);
                    if (player instanceof ServerPlayer serverPlayer) {
                        WarehouseAmmoBridge.remember(serverPlayer, weapon, matched);
                    }
                }
            }
        }
    }

    private boolean isArrow(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW));
    }

    private boolean isCrossbowAmmo(ItemStack stack) {
        return !stack.isEmpty() && (isArrow(stack) || stack.is(Items.FIREWORK_ROCKET));
    }
}