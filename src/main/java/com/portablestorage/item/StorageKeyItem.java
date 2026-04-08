package com.portablestorage.item;

import java.util.UUID;
import java.util.function.Consumer;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class StorageKeyItem extends Item {
    public static final String NBT_OWNER_UUID = "owner_uuid";
    public static final String NBT_OWNER_NAME = "owner_name";

    public StorageKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide())
            return InteractionResult.PASS;

        ServerPlayer player = (ServerPlayer) user;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return InteractionResult.FAIL;

        CompoundTag tag = customData.copyTag();
        String ownerString = tag.getString(NBT_OWNER_UUID).orElse("");
        if (ownerString.isEmpty())
            return InteractionResult.FAIL;

        UUID ownerUuid = UUID.fromString(ownerString);
        if (!ownerUuid.equals(player.getUUID())) {
            player.sendSystemMessage(
                    Component.translatable("message.portablestorage.key_not_owner").withStyle(ChatFormatting.RED),
                    false);
            return InteractionResult.FAIL;
        }

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (warehouse.isEnabled()) {
            player.sendSystemMessage(
                    Component.translatable("message.portablestorage.already_enabled").withStyle(ChatFormatting.YELLOW),
                    false);
            stack.shrink(1);
            return InteractionResult.SUCCESS;
        }

        // 恢复仓库
        warehouse.setEnabled(true);
        stack.shrink(1);

        player.sendSystemMessage(
                Component.translatable("message.portablestorage.reactivated").withStyle(ChatFormatting.GREEN), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player))
            return;
        if (stack.isEmpty() || player.getAbilities().instabuild)
            return;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return;

        CompoundTag tag = customData.copyTag();
        String ownerString = tag.getString(NBT_OWNER_UUID).orElse("");
        if (ownerString.isEmpty())
            return;

        UUID ownerUuid = UUID.fromString(ownerString);
        if (ownerUuid.equals(player.getUUID())) {
            PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
            // 核心功能：仓库钥匙进入绑定者玩家背包时自动使用并消耗
            if (!warehouse.isEnabled()) {
                warehouse.setEnabled(true);
                stack.shrink(1);
                player.sendSystemMessage(
                        Component.translatable("message.portablestorage.reactivated").withStyle(ChatFormatting.GREEN),
                        false);
            } else {
                // 如果仓库已经开启，进入背包也自动消耗（对应“激活状态下进入也消耗”的逻辑一致性）
                stack.shrink(1);
                player.sendSystemMessage(Component.translatable("message.portablestorage.already_enabled")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> adder, TooltipFlag type) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            String ownerString = tag.getString(NBT_OWNER_UUID).orElse("");
            if (!ownerString.isEmpty()) {
                String name = tag.getString(NBT_OWNER_NAME).orElse("");
                adder.accept(Component.translatable("tooltip.portablestorage.bound_to", name)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        adder.accept(
                Component.translatable("tooltip.portablestorage.key_use_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static ItemStack create(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModItems.STORAGE_KEY);
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_OWNER_UUID, player.getUUID().toString());
        // 重新实现 GameProfile.getName() 的效果：使用玩家当前显示名
        tag.putString(NBT_OWNER_NAME, player.getScoreboardName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }
}
