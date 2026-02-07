package com.portablestorage.event;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.PlayerWarehouse.WarehouseType;
import com.portablestorage.config.ModConfig;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WarehouseActivationHandler {
    private static final Map<UUID, Long> CONFIRMATION_MAP = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 10000; // 10秒超时

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide) return InteractionResultHolder.pass(ItemStack.EMPTY);

            ItemStack stack = player.getItemInHand(hand);
            Item fullItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ModConfig.fullWarehouseActivationItem));
            Item baseItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ModConfig.baseWarehouseActivationItem));
            if (fullItem == Items.AIR) fullItem = Items.NETHER_STAR;
            if (baseItem == Items.AIR) baseItem = Items.HEART_OF_THE_SEA;

            WarehouseType targetType = null;
            if (stack.is(fullItem)) targetType = WarehouseType.FULL;
            else if (stack.is(baseItem)) targetType = WarehouseType.BASE;

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
                    player.displayClientMessage(Component.translatable("message.portablestorage.activated." + typeKey, itemName), false);
                    return InteractionResultHolder.success(player.getItemInHand(hand));
                }

                // 逻辑 2: 强制激活 (当前处于禁用状态)
                if (!warehouse.isEnabled()) {
                    // 如果仓库完全为空，则直接激活，不触发警告
                    if (warehouse.isFullyEmpty()) {
                        warehouse.setType(targetType);
                        warehouse.setEnabled(true);
                        stack.shrink(1);
                        String typeKey = targetType == WarehouseType.FULL ? "full" : "base";
                        player.displayClientMessage(Component.translatable("message.portablestorage.activated." + typeKey, itemName), false);
                        return InteractionResultHolder.success(player.getItemInHand(hand));
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
                        player.displayClientMessage(Component.translatable("message.portablestorage.activated." + typeKey, itemName), false);
                        player.displayClientMessage(Component.translatable("message.portablestorage.activated.wiped").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
                        
                        return InteractionResultHolder.success(player.getItemInHand(hand));
                    } else {
                        // 第一次触发：发出警告并记录时间
                        CONFIRMATION_MAP.put(player.getUUID(), now);
                        player.displayClientMessage(Component.translatable("message.portablestorage.activation_warning").withStyle(ChatFormatting.YELLOW), false);
                        player.displayClientMessage(Component.translatable("message.portablestorage.activation_confirm_hint", itemName).withStyle(ChatFormatting.GOLD), false);
                        return InteractionResultHolder.success(player.getItemInHand(hand));
                    }
                }

                // 如果已经开启且等级不匹配
                if (warehouse.isEnabled()) {
                    player.displayClientMessage(Component.translatable("message.portablestorage.already_enabled"), true);
                }
            }

            return InteractionResultHolder.pass(stack);
        });
    }
}

