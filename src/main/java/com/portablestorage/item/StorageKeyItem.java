package com.portablestorage.item;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class StorageKeyItem extends Item {
    public static final String NBT_OWNER_UUID = "owner_uuid";
    public static final String NBT_OWNER_NAME = "owner_name";

    public StorageKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        ServerPlayer player = (ServerPlayer) user;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return InteractionResultHolder.fail(stack);

        CompoundTag tag = customData.copyTag();
        if (!tag.hasUUID(NBT_OWNER_UUID)) return InteractionResultHolder.fail(stack);

        UUID ownerUuid = tag.getUUID(NBT_OWNER_UUID);
        if (!ownerUuid.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.portablestorage.key_not_owner").withStyle(ChatFormatting.RED), false);
            return InteractionResultHolder.fail(stack);
        }

        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(level).getWarehouse(player.getUUID());
        if (warehouse.isEnabled()) {
            player.displayClientMessage(Component.translatable("message.portablestorage.already_enabled").withStyle(ChatFormatting.YELLOW), false);
            return InteractionResultHolder.fail(stack);
        }

        // 恢复仓库
        warehouse.setEnabled(true);
        stack.shrink(1);

        player.displayClientMessage(Component.translatable("message.portablestorage.reactivated").withStyle(ChatFormatting.GREEN), false);
        return InteractionResultHolder.success(user.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.hasUUID(NBT_OWNER_UUID)) {
                String name = tag.getString(NBT_OWNER_NAME);
                tooltip.add(Component.translatable("tooltip.portablestorage.bound_to", name).withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.portablestorage.key_use_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static ItemStack create(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModItems.STORAGE_KEY);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(NBT_OWNER_UUID, player.getUUID());
        tag.putString(NBT_OWNER_NAME, player.getGameProfile().getName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }
}

