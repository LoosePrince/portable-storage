package com.portablestorage.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class BoundBarrelItem extends BlockItem {
    public BoundBarrelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, adder, flag);

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().getString("owner").isPresent()) {
            String ownerName = customData.copyTag().getString("ownerName").orElse("");
            if (ownerName.isEmpty())
                ownerName = "Unknown";

            adder.accept(Component.literal(" "));
            adder.accept(Component
                    .translatable("upgrade.portablestorage.barrel.bound_to",
                            Component.literal(ownerName).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            adder.accept(Component.translatable("container.portablestorage.bound_barrel_unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
