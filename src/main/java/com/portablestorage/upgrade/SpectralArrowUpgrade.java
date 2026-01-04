package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class SpectralArrowUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("spectral_arrow");

    public SpectralArrowUpgrade() {
        super(
            ID,
            null,
            stack -> stack.is(Items.SPECTRAL_ARROW)
        );
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.SPECTRAL_ARROW);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.spectral_arrow.desc").withStyle(ChatFormatting.GRAY));
        return tooltips;
    }
}

