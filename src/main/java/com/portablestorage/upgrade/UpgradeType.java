package com.portablestorage.upgrade;

import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
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

    /**
     * 获取该升级槽位的悬停提示信息
     * @param warehouse 仓库实例
     * @param stack 当前槽位中的物品
     * @return 提示信息列表
     */
    public List<net.minecraft.network.chat.Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<net.minecraft.network.chat.Component> tooltips = new java.util.ArrayList<>();
        tooltips.add(net.minecraft.network.chat.Component.translatable("upgrade." + id.getNamespace() + "." + id.getPath().replace("/", ".") + ".name").withStyle(net.minecraft.ChatFormatting.GOLD));
        return tooltips;
    }

    /**
     * 允许升级定义该槽位的最大堆叠数，默认为 1
     */
    public int getMaxStackSize() {
        return 1;
    }

    // --- 事件钩子 ---
    public void onInstall(PlayerWarehouse warehouse, ItemStack stack) {}
    public void onUninstall(PlayerWarehouse warehouse, ItemStack stack) {}

    // --- 交互钩子 ---
    public void onRightClick(PlayerWarehouse warehouse, Player player) {}
    public void onMiddleClick(PlayerWarehouse warehouse, Player player) {}
}

