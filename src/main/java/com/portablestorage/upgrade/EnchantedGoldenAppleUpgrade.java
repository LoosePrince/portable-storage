package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.network.S2COpenFoodFilterPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class EnchantedGoldenAppleUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("enchanted_golden_apple");
    private static final String TAG_THRESHOLD = "FeedThreshold";
    private static final int[] THRESHOLDS = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20};

    public EnchantedGoldenAppleUpgrade() {
        super(ID, null, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.enchanted_golden_apple.desc").withStyle(ChatFormatting.GRAY));
        
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int threshold = 0;
        if (data != null) {
            threshold = data.copyTag().getInt(TAG_THRESHOLD);
        }
        
        tooltips.add(Component.literal(" "));
        Component statusText = threshold == 0 ? Component.translatable("gui.portablestorage.off").withStyle(ChatFormatting.RED) : Component.literal(String.valueOf(threshold)).withStyle(ChatFormatting.GREEN);
        tooltips.add(Component.translatable("upgrade.portablestorage.enchanted_golden_apple.threshold", statusText).withStyle(ChatFormatting.YELLOW));
        
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.enchanted_golden_apple.toggle_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltips.add(Component.translatable("upgrade.portablestorage.enchanted_golden_apple.filter_hint").withStyle(ChatFormatting.DARK_GRAY));
        
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        if (!stack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                int current = tag.getInt(TAG_THRESHOLD);
                int nextIndex = 0;
                for (int i = 0; i < THRESHOLDS.length; i++) {
                    if (THRESHOLDS[i] == current) {
                        nextIndex = (i + 1) % THRESHOLDS.length;
                        break;
                    }
                }
                tag.putInt(TAG_THRESHOLD, THRESHOLDS[nextIndex]);
            });
            warehouse.markDirty();
            
            int threshold = getThreshold(stack);
            Component status = threshold == 0 ? Component.translatable("gui.portablestorage.off") : Component.literal(String.valueOf(threshold));
            player.displayClientMessage(Component.translatable("upgrade.portablestorage.enchanted_golden_apple.threshold", status), true);
        }
    }

    @Override
    public void onMiddleClick(PlayerWarehouse warehouse, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new S2COpenFoodFilterPayload(warehouse.getFoodFilters(), warehouse.isFoodFilterBlacklist()));
        }
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, ServerPlayer player) {
        if (player.tickCount % 20 != 0) return; // 每秒检查一次

        ItemStack stack = warehouse.getUpgrade(ID);
        int threshold = getThreshold(stack);
        if (threshold == 0) return;

        if (player.getFoodData().getFoodLevel() < threshold) {
            autoEat(warehouse, player);
        }
    }

    private void autoEat(PlayerWarehouse warehouse, ServerPlayer player) {
        List<WarehouseEntry> storage = warehouse.getStorageList();
        List<String> filters = warehouse.getFoodFilters();
        boolean blacklist = warehouse.isFoodFilterBlacklist();
        
         WarehouseEntry bestFood = null;
        long maxCount = 0;

        for (WarehouseEntry entry : storage) {
            ItemStack foodStack = entry.getItemStack();
            FoodProperties food = foodStack.get(DataComponents.FOOD);
            if (food != null) {
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(foodStack.getItem()).toString();
                if (shouldAutoEat(itemId, filters, blacklist) && entry.getCount() > maxCount) {
                    maxCount = entry.getCount();
                    bestFood = entry;
                }
            }
        }

        if (bestFood != null) {
            ItemStack toEat = bestFood.getItemStack().copy();
            toEat.setCount(1);
            
            // 先获取名称，因为 eat 可能会清空堆叠
            Component foodName = toEat.getHoverName();
            
            // 模拟进食
            player.eat(player.level(), toEat);
            bestFood.subtract(1);
            if (bestFood.getCount() <= 0) {
                storage.remove(bestFood);
            }
            warehouse.markDirty();
            
            player.displayClientMessage(Component.translatable("upgrade.portablestorage.enchanted_golden_apple.auto_eat", 
                foodName), true);
        }
    }

    private boolean shouldAutoEat(String itemId, List<String> filters, boolean blacklist) {
        boolean match = false;
        for (String rule : filters) {
            if (matchRule(itemId, rule)) {
                match = true;
                break;
            }
        }
        return blacklist ? !match : match;
    }

    private boolean matchRule(String text, String rule) {
        if (rule.isEmpty()) return false;
        if (rule.startsWith("!") && rule.endsWith("!") && rule.length() > 1) {
            return text.equals(rule.substring(1, rule.length() - 1));
        }
        try {
            String regex = rule.replace(".", "\\.").replace("*", ".*");
            if (!rule.contains("*")) return text.contains(rule);
            return text.matches(regex);
        } catch (Exception e) {
            return text.contains(rule);
        }
    }

    private int getThreshold(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            return data.copyTag().getInt(TAG_THRESHOLD);
        }
        return 0;
    }
}

