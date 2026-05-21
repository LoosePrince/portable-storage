package com.portablestorage.upgrade;

import java.util.List;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.screen.ToolWarehouseScreenHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ToolUpgrade extends UpgradeType {
    public static final Identifier ID = PortableStorage.id("tool");

    public ToolUpgrade() {
        super(ID, null, ToolUpgrade::isToolUpgradeItem);
    }

    public static boolean isToolUpgradeItem(ItemStack stack) {
        return stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.SHOVELS);
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.tool.desc").withStyle(ChatFormatting.GRAY));
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.tool.open_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new ToolWarehouseScreenHandler(syncId, inventory),
                Component.translatable("container.portablestorage.tool_warehouse")));
    }
}