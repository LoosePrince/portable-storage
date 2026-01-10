package com.portablestorage.item;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(NBT_OWNER_UUID)) return InteractionResultHolder.fail(stack);

        UUID ownerUuid = tag.getUUID(NBT_OWNER_UUID);
        if (!ownerUuid.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.portablestorage.key_not_owner").withStyle(ChatFormatting.RED), false);
            return InteractionResultHolder.fail(stack);
        }

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (warehouse.isEnabled()) {
            player.displayClientMessage(Component.translatable("message.portablestorage.already_enabled").withStyle(ChatFormatting.YELLOW), false);
            stack.shrink(1);
            return InteractionResultHolder.success(stack);
        }

        // 恢复仓库
        warehouse.setEnabled(true);
        stack.shrink(1);

        player.displayClientMessage(Component.translatable("message.portablestorage.reactivated").withStyle(ChatFormatting.GREEN), false);
        return InteractionResultHolder.success(user.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (stack.isEmpty() || player.getAbilities().instabuild) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(NBT_OWNER_UUID)) return;

        UUID ownerUuid = tag.getUUID(NBT_OWNER_UUID);
        if (ownerUuid.equals(player.getUUID())) {
            PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
            // 核心功能：仓库钥匙进入绑定者玩家背包时自动使用并消耗
            if (!warehouse.isEnabled()) {
                warehouse.setEnabled(true);
                stack.shrink(1);
                player.displayClientMessage(Component.translatable("message.portablestorage.reactivated").withStyle(ChatFormatting.GREEN), false);
            } else {
                // 如果仓库已经开启，进入背包也自动消耗（对应“激活状态下进入也消耗”的逻辑一致性）
                stack.shrink(1);
                player.displayClientMessage(Component.translatable("message.portablestorage.already_enabled").withStyle(ChatFormatting.YELLOW), false);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag type) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(NBT_OWNER_UUID)) {
            String name = tag.getString(NBT_OWNER_NAME);
            tooltip.add(Component.translatable("tooltip.portablestorage.bound_to", name).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.portablestorage.key_use_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static ItemStack create(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModItems.STORAGE_KEY);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(NBT_OWNER_UUID, player.getUUID());
        tag.putString(NBT_OWNER_NAME, player.getGameProfile().getName());
        // 在 1.20.1 中，使用 NBT 标记来实现附魔光效
        tag.putBoolean("EnchantmentGlintOverride", true);
        return stack;
    }
}

