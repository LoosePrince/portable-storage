package com.portable.storage.mixin;

import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.StorageInventory;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 弩的装填：在装填过程判定并从随身仓库扣除一支箭（若背包无箭）。
 * 与弓不同，弩可以保持装填状态，因此扣减发生在装填时而不是发射时。
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {

    @Inject(method = "onStoppedUsing", at = @At("TAIL"))
    private void portableStorage$consumeOnLoad(ItemStack crossbowStack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (!(user instanceof PlayerEntity player)) return;
        if (world.isClient) return;

        // 检查弩是否已经装填（通过检查 NBT 数据）
        if (!CrossbowItem.isCharged(crossbowStack)) return;

        // 如果玩家已有弹药，交给原版处理，不从仓库扣
        if (hasAnyBolt(player)) return;

        // 检查无限附魔
        boolean hasInfinity = false;
        try {
            var reg = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            var opt = reg.getEntry(Enchantments.INFINITY);
            if (opt.isPresent()) {
                hasInfinity = EnchantmentHelper.getLevel(opt.get(), crossbowStack) > 0;
            }
        } catch (Throwable ignored) {}

        if (player instanceof ServerPlayerEntity) {
            StorageInventory inv = PlayerStorageService.getInventory(player);
            int idx = -1;
            ItemStack matchedAmmo = null;
            for (int i = 0; i < inv.getCapacity(); i++) {
                ItemStack disp = inv.getDisplayStack(i);
                if (!disp.isEmpty() && isCrossbowAmmo(disp) && inv.getCountByIndex(i) > 0) { 
                    idx = i; 
                    matchedAmmo = disp;
                    break; 
                }
            }
            if (idx < 0) return; // 仓库也没有弹药

            // 检查是否是特殊弹药（药箭、光灵箭、烟花火箭），这些弹药即使有无限附魔也要扣除
            boolean isSpecialAmmo = matchedAmmo.isOf(Items.TIPPED_ARROW) || 
                                   matchedAmmo.isOf(Items.SPECTRAL_ARROW) || 
                                   matchedAmmo.isOf(Items.FIREWORK_ROCKET);
            
            // 如果没有无限附魔，或者是有无限附魔但使用的是特殊弹药，才扣除 1 个弹药
            if (!hasInfinity || isSpecialAmmo) {
                inv.takeByIndex(idx, 1, world.getTime());
            }
        }
    }

    private static boolean hasAnyBolt(PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (isCrossbowAmmo(s)) return true;
        }
        return false;
    }

    private static boolean isCrossbowAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // 原版跨弩弹药：普通/药水/光灵箭；烟花火箭也可作为弹药
        return stack.isOf(Items.ARROW) || stack.isOf(Items.TIPPED_ARROW) || stack.isOf(Items.SPECTRAL_ARROW) || stack.isOf(Items.FIREWORK_ROCKET);
    }
}
