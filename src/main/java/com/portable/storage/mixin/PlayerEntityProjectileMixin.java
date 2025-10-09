package com.portable.storage.mixin;

import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.StorageInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 允许在背包没有箭时，若随身仓库中存在任意箭变体，则返回一支“虚拟箭”以开始蓄力。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityProjectileMixin {

    @Inject(method = "getProjectileType", at = @At("HEAD"), cancellable = true)
    private void portableStorage$returnArrowFromStorage(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;

        // 处理弓的箭
        if (weapon.getItem() instanceof BowItem) {
            // 玩家背包已有箭，走原版
            for (int i = 0; i < self.getInventory().size(); i++) {
                ItemStack s = self.getInventory().getStack(i);
                if (isArrow(s)) return;
            }

            // 背包无箭，检查随身仓库
            StorageInventory inv = PlayerStorageService.getInventory(self);
            int idx = -1;
            for (int i = 0; i < inv.getCapacity(); i++) {
                ItemStack disp = inv.getDisplayStack(i);
                if (!disp.isEmpty() && isArrow(disp) && inv.getCountByIndex(i) > 0) {
                    idx = i; break;
                }
            }
            if (idx < 0) return; // 仓库也没有箭

            // 返回与仓库中首个箭变体一致的 1 个箭，允许开始蓄力
            ItemStack variant = inv.getDisplayStack(idx).copy();
            variant.setCount(1);
            cir.setReturnValue(variant);
        }
        // 处理弩的弹药
        else if (weapon.getItem() instanceof net.minecraft.item.CrossbowItem) {
            // 玩家背包已有弹药，走原版
            for (int i = 0; i < self.getInventory().size(); i++) {
                ItemStack s = self.getInventory().getStack(i);
                if (isCrossbowAmmo(s)) return;
            }

            // 背包无弹药，检查随身仓库
            StorageInventory inv = PlayerStorageService.getInventory(self);
            int idx = -1;
            for (int i = 0; i < inv.getCapacity(); i++) {
                ItemStack disp = inv.getDisplayStack(i);
                if (!disp.isEmpty() && isCrossbowAmmo(disp) && inv.getCountByIndex(i) > 0) {
                    idx = i; break;
                }
            }
            if (idx < 0) return; // 仓库也没有弹药

            // 返回与仓库中首个弹药变体一致的 1 个弹药，允许开始装填
            ItemStack variant = inv.getDisplayStack(idx).copy();
            variant.setCount(1);
            cir.setReturnValue(variant);
        }
    }

    private static boolean isArrow(ItemStack stack) {
        return !stack.isEmpty() && (stack.isOf(Items.ARROW) || stack.isOf(Items.TIPPED_ARROW) || stack.isOf(Items.SPECTRAL_ARROW));
    }

    private static boolean isCrossbowAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // 原版跨弩弹药：普通/药水/光灵箭；烟花火箭也可作为弹药
        return stack.isOf(Items.ARROW) || stack.isOf(Items.TIPPED_ARROW) || stack.isOf(Items.SPECTRAL_ARROW) || stack.isOf(Items.FIREWORK_ROCKET);
    }
}


