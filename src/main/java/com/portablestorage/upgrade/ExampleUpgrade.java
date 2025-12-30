package com.portablestorage.upgrade;

import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ExampleUpgrade extends UpgradeType {
    public ExampleUpgrade() {
        super(
            ResourceLocation.fromNamespaceAndPath("portablestorage", "example_upgrade"),
            ResourceLocation.withDefaultNamespace("textures/item/diamond.png"),
            stack -> stack.is(Items.DIAMOND)
        );
    }

    @Override
    public void onInstall(PlayerWarehouse warehouse, ItemStack stack) {
        // 安装时逻辑
        com.portablestorage.PortableStorage.LOGGER.info("钻石升级已安装！");
    }

    @Override
    public void onUninstall(PlayerWarehouse warehouse, ItemStack stack) {
        // 卸载时逻辑
        com.portablestorage.PortableStorage.LOGGER.info("钻石升级已卸载！");
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        player.displayClientMessage(Component.literal("你右键点击了钻石升级！"), false);
    }
}

