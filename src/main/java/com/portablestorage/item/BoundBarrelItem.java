package com.portablestorage.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class BoundBarrelItem extends BlockItem {
    public BoundBarrelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID("owner")) {
            String ownerName = tag.getString("ownerName");
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

