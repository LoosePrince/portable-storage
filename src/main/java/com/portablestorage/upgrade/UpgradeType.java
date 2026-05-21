package com.portablestorage.upgrade;

import java.util.List;
import java.util.function.Predicate;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 升级类型基类
 * 定义仓库升级的基本接口和行为，所有升级类型都应继承此类
 */
public abstract class UpgradeType {
    private final Identifier id;
    private final Identifier icon;
    private final Predicate<ItemStack> validator;

    public UpgradeType(Identifier id, Identifier icon, Predicate<ItemStack> validator) {
        this.id = id;
        this.icon = icon;
        this.validator = validator;
    }

    public Identifier getId() {
        return id;
    }

    public Identifier getIcon() {
        return icon;
    }

    /**
     * 获取用于显示的物品图标。如果返回非空，则渲染器优先使用物品图标。
     */
    public ItemStack getIconStack() {
        return ItemStack.EMPTY;
    }

    public boolean isItemValid(ItemStack stack) {
        return validator.test(stack);
    }

    /**
     * 获取该升级槽位的悬停提示信息
     * 
     * @param warehouse 仓库实例
     * @param stack     当前槽位中的物品
     * @return 提示信息列表
     */
    public List<net.minecraft.network.chat.Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<net.minecraft.network.chat.Component> tooltips = new java.util.ArrayList<>();
        tooltips.add(net.minecraft.network.chat.Component
                .translatable("upgrade." + id.getNamespace() + "." + id.getPath().replace("/", ".") + ".name")
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        return tooltips;
    }

    /**
     * 允许升级定义该槽位的最大堆叠数，默认为 1
     */
    public int getMaxStackSize() {
        return 1;
    }

    // ========== 事件钩子 ==========
    public void onInstall(PlayerWarehouse warehouse, ItemStack stack) {
    }

    public void onUninstall(PlayerWarehouse warehouse, ItemStack stack) {
    }

    // ========== 交互钩子 ==========
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
    }

    public void onMiddleClick(PlayerWarehouse warehouse, Player player) {
    }

    /**
     * 是否需要服务端 Tick 调度。默认关闭，只有存在周期行为的升级开启。
     */
    public boolean requiresServerTick() {
        return false;
    }

    /**
     * 服务端每 Tick 调用 (仅在已安装此升级且仓库启用时)
     */
    public void serverTick(PlayerWarehouse warehouse, net.minecraft.server.level.ServerPlayer player) {
    }
}
