package com.portablestorage.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.PlayerWarehouse.WarehouseType;
import com.portablestorage.config.ModConfig;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WarehouseActivationHandler {
    private static final Map<UUID, Long> CONFIRMATION_MAP = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 10000; // 10秒超时

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide())
                return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            Item fullItem = BuiltInRegistries.ITEM
                    .get(Identifier.tryParse(ModConfig.fullWarehouseActivationItem))
                    .map(holder -> holder.value())
                    .orElse(Items.AIR);
            Item baseItem = BuiltInRegistries.ITEM
                    .get(Identifier.tryParse(ModConfig.baseWarehouseActivationItem))
                    .map(holder -> holder.value())
                    .orElse(Items.AIR);
            if (fullItem == Items.AIR)
                fullItem = Items.NETHER_STAR;
            if (baseItem == Items.AIR)
                baseItem = Items.HEART_OF_THE_SEA;

            WarehouseType targetType = null;
            if (stack.is(fullItem))
                targetType = WarehouseType.FULL;
            else if (stack.is(baseItem))
                targetType = WarehouseType.BASE;

            if (targetType != null) {
                PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
                WarehouseType currentType = warehouse.getType();
                Item activationItem = targetType == WarehouseType.FULL ? fullItem : baseItem;
                Component itemName = Component.translatable(activationItem.getDescriptionId());

                // 逻辑 1: 升级激活 (当前已开启，且目标等级更高)
                if (warehouse.isEnabled() && targetType.ordinal() > currentType.ordinal()) {
                    warehouse.setType(targetType);
                    stack.shrink(1);
                    String typeKey = targetType == WarehouseType.FULL ? "full" : "base";
                    player.sendSystemMessage(
                            Component.translatable("message.portablestorage.activated." + typeKey, itemName));
                    return InteractionResult.SUCCESS;
                }

                // 逻辑 2: 强制激活 (当前处于禁用状态)
                if (!warehouse.isEnabled()) {
                    // 如果仓库完全为空，则直接激活，不触发警告
                    if (warehouse.isFullyEmpty()) {
                        warehouse.setType(targetType);
                        warehouse.setEnabled(true);
                        stack.shrink(1);
                        String typeKey = targetType == WarehouseType.FULL ? "full" : "base";
                        player.sendSystemMessage(
                                Component.translatable("message.portablestorage.activated." + typeKey, itemName));
                        return InteractionResult.SUCCESS;
                    }

                    long now = System.currentTimeMillis();
                    long lastAttempt = CONFIRMATION_MAP.getOrDefault(player.getUUID(), 0L);

                    if (now - lastAttempt < CONFIRMATION_TIMEOUT) {
                        // 执行二次确认后的逻辑：清除数据并重新开启
                        warehouse.clearContent();
                        warehouse.setType(targetType);
                        warehouse.setEnabled(true);
                        stack.shrink(1);
                        CONFIRMATION_MAP.remove(player.getUUID());

                        String typeKey = targetType == WarehouseType.FULL ? "full" : "base";
                        player.sendSystemMessage(
                                Component.translatable("message.portablestorage.activated." + typeKey, itemName));
                        player.sendSystemMessage(Component.translatable("message.portablestorage.activated.wiped")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

                        return InteractionResult.SUCCESS;
                    } else {
                        // 第一次触发：发出警告并记录时间
                        CONFIRMATION_MAP.put(player.getUUID(), now);
                        player.sendSystemMessage(Component.translatable("message.portablestorage.activation_warning")
                                .withStyle(ChatFormatting.YELLOW));
                        player.sendSystemMessage(
                                Component.translatable("message.portablestorage.activation_confirm_hint", itemName)
                                        .withStyle(ChatFormatting.GOLD));
                        return InteractionResult.SUCCESS;
                    }
                }

                // 如果已经开启且等级不匹配
                if (warehouse.isEnabled()) {
                    player.sendSystemMessage(Component.translatable("message.portablestorage.already_enabled"));
                }
            }

            return InteractionResult.PASS;
        });
    }
}
