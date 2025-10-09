package com.portable.storage.mixin;

import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.StorageInventory;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在弓松手时，如果玩家背包中没有箭，则尝试从随身仓库取用箭；
 * 若有无限附魔，只要仓库存在任意箭即可无限射击且不扣减。
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin {

    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    private void portableStorage$maybeConsumeFromStorage(ItemStack bowStack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (!(user instanceof PlayerEntity player)) return;
        if (world.isClient) return;

        // 如果玩家已有箭（含创造模式或光灵箭等原版逻辑会处理），这里仅在“即将无箭时”兜底
        boolean hasArrowInInventory = hasAnyArrow(player);
        if (hasArrowInInventory) return;

        // 检查无限
        boolean hasInfinity = false;
        try {
            var reg = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            var opt = reg.getEntry(Enchantments.INFINITY);
            if (opt.isPresent()) {
                hasInfinity = EnchantmentHelper.getLevel(opt.get(), bowStack) > 0;
            }
        } catch (Throwable ignored) {}

        if (player instanceof ServerPlayerEntity) {
            StorageInventory inv = PlayerStorageService.getInventory(player);

            // 扫描是否存在任意箭
            int matchIndex = -1;
            ItemStack matchedArrow = null;
            for (int i = 0; i < inv.getCapacity(); i++) {
                ItemStack disp = inv.getDisplayStack(i);
                if (disp.isEmpty()) continue;
                if (isArrow(disp)) { 
                    matchIndex = i; 
                    matchedArrow = disp;
                    break; 
                }
            }
            if (matchIndex < 0) return; // 仓库也没有箭

            // 检查是否是特殊箭（药箭、光灵箭），这些箭即使有无限附魔也要扣除
            boolean isSpecialArrow = matchedArrow.isOf(Items.TIPPED_ARROW) || matchedArrow.isOf(Items.SPECTRAL_ARROW);
            
            // 如果没有无限附魔，或者是有无限附魔但使用的是特殊箭，就预扣 1 支
            if (!hasInfinity || isSpecialArrow) {
                inv.takeByIndex(matchIndex, 1, world.getTime());
            }
        }
    }

    private static boolean hasAnyArrow(PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && isArrow(s)) return true;
        }
        return false;
    }

    private static boolean isArrow(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.ARROW || item == Items.TIPPED_ARROW || item == Items.SPECTRAL_ARROW;
    }
}


