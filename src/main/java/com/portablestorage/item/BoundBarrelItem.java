package com.portablestorage.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class BoundBarrelItem extends BlockItem {
    public BoundBarrelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().hasUUID("owner")) {
            String ownerName = customData.copyTag().getString("ownerName");
            if (ownerName.isEmpty()) ownerName = "Unknown";
            
            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("upgrade.portablestorage.barrel.bound_to", 
                Component.literal(ownerName).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("container.portablestorage.bound_barrel_unbound").withStyle(ChatFormatting.GRAY));
        }
    }
}

