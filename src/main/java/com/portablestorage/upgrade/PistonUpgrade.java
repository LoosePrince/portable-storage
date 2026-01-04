package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class PistonUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("piston");

    public PistonUpgrade() {
        super(ID, null, stack -> stack.is(Items.PISTON) || stack.is(Items.STICKY_PISTON));
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.PISTON);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.piston.desc").withStyle(ChatFormatting.GRAY));
        return tooltips;
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, ServerPlayer player) {
        // 自动补充主副手
        replenishHand(warehouse, player, InteractionHand.MAIN_HAND);
        replenishHand(warehouse, player, InteractionHand.OFF_HAND);
    }

    private void replenishHand(PlayerWarehouse warehouse, ServerPlayer player, InteractionHand hand) {
        ItemStack handStack = player.getItemInHand(hand);
        if (handStack.isEmpty() || !handStack.isStackable()) return;
        
        if (handStack.getCount() < handStack.getMaxStackSize()) {
            int toAdd = handStack.getMaxStackSize() - handStack.getCount();
            ItemStack taken = WarehouseManager.takeMatching(warehouse, handStack, toAdd, true);
            if (!taken.isEmpty()) {
                handStack.grow(taken.getCount());
            }
        }
    }
}

