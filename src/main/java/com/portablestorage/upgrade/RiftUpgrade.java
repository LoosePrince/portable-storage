package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.world.SpaceRiftManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RiftUpgrade extends UpgradeType {
    public static final Identifier ID = PortableStorage.id("rift");
    public static final Identifier ICON = PortableStorage.id("textures/gui/upgrade/rift.png");

    public RiftUpgrade() {
        super(ID, ICON, stack -> {
            Item item = BuiltInRegistries.ITEM
                    .get(Identifier.tryParse(ModConfig.riftUpgradeItem))
                    .map(holder -> holder.value())
                    .orElse(Items.AIR);
            if (item == Items.AIR)
                item = Items.DRAGON_EGG;
            return stack.is(item);
        });
    }

    @Override
    public ItemStack getIconStack() {
        Item item = BuiltInRegistries.ITEM
                .get(Identifier.tryParse(ModConfig.riftUpgradeItem))
                .map(holder -> holder.value())
                .orElse(Items.AIR);
        if (item == Items.AIR)
            item = Items.DRAGON_EGG;
        return new ItemStack(item);
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> getTooltip(PlayerWarehouse warehouse,
            net.minecraft.world.item.ItemStack stack) {
        java.util.List<net.minecraft.network.chat.Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(net.minecraft.network.chat.Component.translatable("upgrade.portablestorage.rift.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltips.add(net.minecraft.network.chat.Component.literal(" "));
        tooltips.add(net.minecraft.network.chat.Component.translatable("upgrade.portablestorage.rift.interaction_hint")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            SpaceRiftManager.enterOrExitRift(serverPlayer, warehouse);
        }
    }

    @Override
    public boolean requiresServerTick() {
        return true;
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, ServerPlayer player) {
        // 检查虚空掉落和边界
        if (player.level().dimension().equals(SpaceRiftManager.DIMENSION_KEY)) {
            SpaceRiftManager.handleVoidFall(player, warehouse);
            SpaceRiftManager.checkAndTeleportBack(player, warehouse);
            SpaceRiftManager.tickBorderResend(player, warehouse);
        }
    }
}
