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

        // 检查弩是否已经装填
        ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (chargedProjectiles == null || chargedProjectiles.isEmpty()) return;

        // 如果玩家背包有弹药，优先消耗背包的（原版逻辑会处理）
        if (hasAnyAmmo(player)) return;

        PlayerWarehouse warehouse = ModComponents.getWarehouse(player.getServer(), player.getUUID());
        if (warehouse == null || !warehouse.isEnabled()) return;

        // 查找合适的弹药进行扣除
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
            WarehouseManager.takeMatching(warehouse, matchedAmmo, 1, true);
        }
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

