package com.portablestorage.upgrade;

import java.util.List;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.logic.WarehouseManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

/**
 * 漏斗升级
 * 自动拾取周围的掉落物
 */
public class HopperUpgrade extends UpgradeType {
    public static final Identifier ID = PortableStorage.id("hopper");

    public HopperUpgrade() {
        super(
                ID,
                null,
                stack -> stack.is(Items.HOPPER));
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.HOPPER);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.desc").withStyle(ChatFormatting.GRAY));

        boolean enabled = isHopperEnabled(stack);
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.status",
                Component.translatable(enabled ? "gui.portablestorage.on" : "gui.portablestorage.off")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .withStyle(ChatFormatting.YELLOW));

        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.config_range", ModConfig.hopperRange)
                .withStyle(ChatFormatting.BLUE));
        tooltips.add(
                Component.translatable("upgrade.portablestorage.hopper.config_frequency", ModConfig.hopperFrequency)
                        .withStyle(ChatFormatting.BLUE));

        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.toggle_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.filter_hint")
                .withStyle(ChatFormatting.DARK_GRAY));

        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        if (!stack.isEmpty()) {
            boolean current = isHopperEnabled(stack);
            setHopperEnabled(stack, !current);
            warehouse.markDirty();

            player.displayClientMessage(Component.translatable("upgrade.portablestorage.hopper.toggled",
                    Component.translatable(!current ? "gui.portablestorage.on" : "gui.portablestorage.off")
                            .withStyle(!current ? ChatFormatting.GREEN : ChatFormatting.RED)),
                    true);
        }
    }

    @Override
    public void onMiddleClick(PlayerWarehouse warehouse, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
                    new com.portablestorage.network.S2COpenHopperFilterPayload(warehouse.getHopperFilters(),
                            warehouse.isHopperFilterBlacklist()));
        }
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, ServerPlayer player) {
        // 关键：从仓库获取最新的漏斗物品堆叠
        ItemStack hopperStack = warehouse.getUpgrade(ID);
        if (hopperStack.isEmpty() || !isHopperEnabled(hopperStack))
            return;

        // 频率控制
        int interval = (int) (ModConfig.hopperFrequency * 20);
        if (interval < 1)
            interval = 1;
        if (player.tickCount % interval != 0)
            return;

        double range = ModConfig.hopperRange;
        AABB area = new AABB(
                player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range, player.getZ() + range);

        // 获取过滤列表和模式
        List<String> filters = warehouse.getHopperFilters();
        boolean blacklist = warehouse.isHopperFilterBlacklist();

        // 获取区域内的所有活跃掉落物
        ServerLevel level = (ServerLevel) player.level();
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            if (!entity.isAlive())
                return false;

            ItemStack itemStack = entity.getItem();
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
            boolean match = false;
            for (String rule : filters) {
                if (matchRule(itemId, rule)) {
                    match = true;
                    break;
                }
            }
            // 如果是黑名单，匹配到规则则不拾取；如果是白名单，未匹配到规则则不拾取
            return blacklist ? !match : match;
        });

        boolean pickedAny = false;
        for (ItemEntity itemEntity : items) {
            ItemStack itemStack = itemEntity.getItem();
            if (itemStack.isEmpty())
                continue;

            int originalCount = itemStack.getCount();

            // 尝试流体转换
            ItemStack processedStack = WarehouseManager.addFluid(warehouse, itemStack, player);

            if (processedStack != itemStack || processedStack.getCount() != originalCount) {
                pickedAny = true;
                if (processedStack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(processedStack);
                }
                continue;
            }

            // 常规物品存入
            WarehouseManager.addItem(warehouse, itemStack, player);
            if (itemStack.getCount() != originalCount) {
                pickedAny = true;
                if (itemStack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(itemStack);
                }
            }
        }

        if (pickedAny) {
            warehouse.markDirty();
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f,
                    ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f);
        }
    }

    private boolean matchRule(String text, String rule) {
        if (rule.isEmpty())
            return false;

        // 精确匹配：!minecraft:dirt!
        if (rule.startsWith("!") && rule.endsWith("!") && rule.length() > 1) {
            return text.equals(rule.substring(1, rule.length() - 1));
        }

        // 模糊匹配
        try {
            String regex = rule.replace(".", "\\.").replace("*", ".*");
            if (!rule.contains("*")) {
                return text.contains(rule);
            }
            return text.matches(regex);
        } catch (Exception e) {
            return text.contains(rule);
        }
    }

    public static boolean isHopperEnabled(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return !customData.copyTag().contains("disabled");
        }
        return true;
    }

    public static void setHopperEnabled(ItemStack stack, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (enabled) {
                tag.remove("disabled");
            } else {
                tag.putBoolean("disabled", true);
            }
        });
    }
}
