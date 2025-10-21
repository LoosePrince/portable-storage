package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.storage.StorageInventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

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

            // 背包无箭，检查新版随身仓库
            if (self instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = (ServerPlayerEntity) self;
                var server = player.getServer();
                if (server != null) {
                    // 构建合并视图（旧版+新版）
                    StorageInventory merged = ServerNetworkingHandlers.buildMergedSnapshot(player);
                    
                    // 优先查找普通箭
                    int spectralIdx = -1;
                    int normalIdx = -1;
                    ItemStack matchedArrow = null;
                    
                    for (int i = 0; i < merged.getCapacity(); i++) {
                        ItemStack disp = merged.getDisplayStack(i);
                        if (!disp.isEmpty() && isArrow(disp) && merged.getCountByIndex(i) > 0) {
                            if (disp.isOf(Items.ARROW)) {
                                normalIdx = i;
                                matchedArrow = disp;
                                break; // 优先使用普通箭
                            } else if (disp.isOf(Items.SPECTRAL_ARROW) && spectralIdx == -1) {
                                spectralIdx = i;
                                if (matchedArrow == null) {
                                    matchedArrow = disp;
                                }
                            }
                        }
                    }
                    
                    if (matchedArrow == null) return; // 仓库也没有箭

                    // 如果有普通箭，直接使用普通箭
                    if (normalIdx >= 0) {
                        ItemStack variant = merged.getDisplayStack(normalIdx).copy();
                        variant.setCount(1);
                        cir.setReturnValue(variant);
                    } else if (spectralIdx >= 0) {
                        // 没有普通箭但有光灵箭，使用光灵箭
                        ItemStack variant = merged.getDisplayStack(spectralIdx).copy();
                        variant.setCount(1);
                        cir.setReturnValue(variant);
                    } else {
                        // 其他情况，返回与仓库中首个箭变体一致的 1 个箭
                        ItemStack variant = merged.getDisplayStack(spectralIdx).copy();
                        variant.setCount(1);
                        cir.setReturnValue(variant);
                    }
                }
            }
        }
        // 处理弩的弹药
        else if (weapon.getItem() instanceof net.minecraft.item.CrossbowItem) {
            // 玩家背包已有弹药，走原版
            for (int i = 0; i < self.getInventory().size(); i++) {
                ItemStack s = self.getInventory().getStack(i);
                if (isCrossbowAmmo(s)) return;
            }

            // 背包无弹药，检查新版随身仓库
            if (self instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = (ServerPlayerEntity) self;
                var server = player.getServer();
                if (server != null) {
                    // 构建合并视图（旧版+新版）
                    StorageInventory merged = ServerNetworkingHandlers.buildMergedSnapshot(player);
                    
                    // 优先查找普通箭
                    int spectralIdx = -1;
                    int normalIdx = -1;
                    ItemStack matchedAmmo = null;
                    
                    for (int i = 0; i < merged.getCapacity(); i++) {
                        ItemStack disp = merged.getDisplayStack(i);
                        if (!disp.isEmpty() && isCrossbowAmmo(disp) && merged.getCountByIndex(i) > 0) {
                            if (disp.isOf(Items.ARROW)) {
                                normalIdx = i;
                                matchedAmmo = disp;
                                break; // 优先使用普通箭
                            } else if (disp.isOf(Items.SPECTRAL_ARROW) && spectralIdx == -1) {
                                spectralIdx = i;
                                if (matchedAmmo == null) {
                                    matchedAmmo = disp;
                                }
                            }
                        }
                    }
                    
                    if (matchedAmmo == null) return; // 仓库也没有弹药

                    // 如果有普通箭，直接使用普通箭
                    if (normalIdx >= 0) {
                        ItemStack variant = merged.getDisplayStack(normalIdx).copy();
                        variant.setCount(1);
                        cir.setReturnValue(variant);
                    } else if (spectralIdx >= 0) {
                        // 没有普通箭但有光灵箭，使用光灵箭
                        ItemStack variant = merged.getDisplayStack(spectralIdx).copy();
                        variant.setCount(1);
                        cir.setReturnValue(variant);
                    } else {
                        // 其他情况，返回与仓库中首个弹药变体一致的 1 个弹药
                        ItemStack variant = merged.getDisplayStack(spectralIdx).copy();
                        variant.setCount(1);
                        cir.setReturnValue(variant);
                    }
                }
            }
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


