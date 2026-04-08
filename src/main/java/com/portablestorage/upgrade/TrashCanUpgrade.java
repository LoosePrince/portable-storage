package com.portablestorage.upgrade;

import java.util.List;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 垃圾桶升级
 * 允许放入任何物品，右键或退出游戏时清空
 */
public class TrashCanUpgrade extends UpgradeType {
    public static final Identifier ID = PortableStorage.id("trash_can");

    public TrashCanUpgrade() {
        super(
                ID,
                PortableStorage.id("textures/gui/icon/delete.png"),
                stack -> true // 允许放入任何物品
        );
    }

    @Override
    public int getMaxStackSize() {
        return 64; // 允许放入整组物品
    }

    @Override
    public List<net.minecraft.network.chat.Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<net.minecraft.network.chat.Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(net.minecraft.network.chat.Component.translatable("upgrade.portablestorage.trash_can.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (!stack.isEmpty()) {
            tooltips.add(net.minecraft.network.chat.Component.literal(" "));
            tooltips.add(
                    net.minecraft.network.chat.Component.translatable("upgrade.portablestorage.trash_can.clear_hint")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        // 右键点击时清空垃圾桶内的物品
        if (!warehouse.getUpgrade(ID).isEmpty()) {
            warehouse.setUpgrade(ID, ItemStack.EMPTY);
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("upgrade.portablestorage.trash_can.cleared"));
        }
    }
}
