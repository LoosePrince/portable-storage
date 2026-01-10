package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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
        
        boolean enabled = is3x3Enabled(stack);
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.workbench.status", 
            Component.translatable(enabled ? "gui.portablestorage.on" : "gui.portablestorage.off")
                .withStyle(enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED)
        ).withStyle(net.minecraft.ChatFormatting.YELLOW));
        
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.workbench.toggle_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        if (!stack.isEmpty()) {
            boolean current = is3x3Enabled(stack);
            set3x3Enabled(stack, !current);
            warehouse.markDirty();
            
            player.displayClientMessage(Component.translatable("upgrade.portablestorage.workbench.toggled", 
                Component.translatable(!current ? "gui.portablestorage.on" : "gui.portablestorage.off")
                    .withStyle(!current ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED)
            ), true);
        }
    }

    public static boolean is3x3Enabled(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null) {
            return !tag.contains("disabled");
        }
        return true; // 默认开启
    }

    public static void set3x3Enabled(ItemStack stack, boolean enabled) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        if (enabled) {
            tag.remove("disabled");
        } else {
            tag.putBoolean("disabled", true);
        }
    }
}

