package com.portablestorage.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class VirtualFluidItem extends Item {
    public VirtualFluidItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag type) {
        adder.accept(Component.literal(" "));
        adder.accept(Component.translatable("tooltip.portablestorage.fluid_take_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, adder, type);
    }
}
