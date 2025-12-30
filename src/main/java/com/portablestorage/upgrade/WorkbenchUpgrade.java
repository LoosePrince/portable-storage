package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

/**
 * 工作台升级
 * 允许玩家通过仓库界面打开合成表
 */
public class WorkbenchUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("workbench");

    public WorkbenchUpgrade() {
        super(
            ID,
            null, // 不再使用简单的 PNG 贴图
            stack -> stack.is(Items.CRAFTING_TABLE)
        );
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.CRAFTING_TABLE);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.workbench.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
        return tooltips;
    }
}

