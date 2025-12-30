package com.portablestorage.upgrade;

import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 升级类型定义
 */
public abstract class UpgradeType {
    private final ResourceLocation id;
    private final ResourceLocation icon;
    private final Predicate<ItemStack> validator;

    public UpgradeType(ResourceLocation id, ResourceLocation icon, Predicate<ItemStack> validator) {
        this.id = id;
        this.icon = icon;
        this.validator = validator;
    }

    public ResourceLocation getId() { return id; }
    public ResourceLocation getIcon() { return icon; }

    public boolean isItemValid(ItemStack stack) {
        return validator.test(stack);
    }

    // --- 事件钩子 ---
    public void onInstall(PlayerWarehouse warehouse, ItemStack stack) {}
    public void onUninstall(PlayerWarehouse warehouse, ItemStack stack) {}

    // --- 交互钩子 ---
    public void onRightClick(PlayerWarehouse warehouse, Player player) {}
    public void onMiddleClick(PlayerWarehouse warehouse, Player player) {}
}

