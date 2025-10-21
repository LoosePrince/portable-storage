package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.newstore.NewStoreService;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.storage.UpgradeInventory;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

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
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            UpgradeInventory upgrades = PlayerStorageService.getUpgradeInventory(player);

            // 检查是否有光灵箭升级
            boolean hasSpectralArrowUpgrade = upgrades.isSpectralArrowUpgradeActive();

            // 构建合并视图（旧版+新版）
            StorageInventory merged = ServerNetworkingHandlers.buildMergedSnapshot(serverPlayer);

            // 优先查找普通箭，与PlayerEntityProjectileMixin保持一致
            int spectralIdx = -1;
            ItemStack matchedArrow = null;
            int matchIndex = -1;
            
            for (int i = 0; i < merged.getCapacity(); i++) {
                ItemStack disp = merged.getDisplayStack(i);
                if (disp.isEmpty()) continue;
                if (isArrow(disp) && merged.getCountByIndex(i) > 0) {
                    if (disp.isOf(Items.ARROW)) {
                        matchedArrow = disp;
                        matchIndex = i;
                        break; // 优先使用普通箭
                    } else if (disp.isOf(Items.SPECTRAL_ARROW) && spectralIdx == -1) {
                        spectralIdx = i;
                        if (matchedArrow == null) {
                            matchedArrow = disp;
                            matchIndex = i;
                        }
                    }
                }
            }
            if (matchedArrow == null) return; // 仓库也没有箭

            // 检查是否是特殊箭（药箭、光灵箭），这些箭即使有无限附魔也要扣除
            boolean isSpecialArrow = matchedArrow.isOf(Items.TIPPED_ARROW) || matchedArrow.isOf(Items.SPECTRAL_ARROW);
            
            // 如果有光灵箭升级且使用的是普通箭，按普通箭处理（但会在命中时施加光灵效果）
            if (hasSpectralArrowUpgrade && matchedArrow.isOf(Items.ARROW)) {
                // 按普通箭处理，有无限附魔时普通箭不会被消耗
                if (!hasInfinity) {
                    // 没有无限附魔，从新版存储扣除1支普通箭
                    NewStoreService.takeForOnlinePlayer((ServerPlayerEntity) player, matchedArrow, 1);
                }
            } else {
                // 没有光灵箭升级或使用的不是普通箭，按原逻辑处理
                // 光灵箭按原版逻辑处理（有无限附魔时仍会被消耗）
                if (!hasInfinity || isSpecialArrow) {
                    NewStoreService.takeForOnlinePlayer((ServerPlayerEntity) player, matchedArrow, 1);
                }
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


