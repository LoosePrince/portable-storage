package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 漏斗升级
 * 自动拾取周围的掉落物
 */
public class HopperUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("hopper");

    public HopperUpgrade() {
        super(
            ID,
            null,
            stack -> stack.is(Items.HOPPER)
        );
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
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
        ).withStyle(ChatFormatting.YELLOW));
        
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.toggle_hint").withStyle(ChatFormatting.DARK_GRAY));
        
        // 显示当前配置信息
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.config_range", ModConfig.hopperRange).withStyle(ChatFormatting.BLUE));
        tooltips.add(Component.translatable("upgrade.portablestorage.hopper.config_frequency", ModConfig.hopperFrequency).withStyle(ChatFormatting.BLUE));
        
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
                    .withStyle(!current ? ChatFormatting.GREEN : ChatFormatting.RED)
            ), true);
        }
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, net.minecraft.server.level.ServerPlayer player) {
        // 关键：从仓库获取最新的漏斗物品堆叠
        ItemStack hopperStack = warehouse.getUpgrade(ID);
        if (hopperStack.isEmpty() || !isHopperEnabled(hopperStack)) return;

        // 频率控制：使用玩家的 tickCount 替代 NBT 存储，避免写回失败
        int interval = (int) (ModConfig.hopperFrequency * 20);
        if (interval < 1) interval = 1;
        if (player.tickCount % interval != 0) return;

        double range = ModConfig.hopperRange;
        // 使用与旧项目一致的 Box 构建方式：以玩家脚底为中心
        AABB area = new AABB(
            player.getX() - range, player.getY() - range, player.getZ() - range,
            player.getX() + range, player.getY() + range, player.getZ() + range
        );
        
        // 获取区域内的所有活跃掉落物
        List<ItemEntity> items = player.serverLevel().getEntitiesOfClass(ItemEntity.class, area, entity -> entity.isAlive());

        boolean pickedAny = false;
        for (ItemEntity itemEntity : items) {
            ItemStack itemStack = itemEntity.getItem();
            if (itemStack.isEmpty()) continue;

            int originalCount = itemStack.getCount();
            
            // 1. 尝试流体转换（如拾取到岩浆桶，会自动变为仓库流体储量）
            ItemStack processedStack = WarehouseManager.addFluid(warehouse, itemStack, player);
            
            // 如果数量变了或物品变了（说明被 addFluid 处理了）
            if (processedStack != itemStack || processedStack.getCount() != originalCount) {
                pickedAny = true;
                if (processedStack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(processedStack);
                }
                continue; 
            }

            // 2. 常规物品存入
            WarehouseManager.addItem(warehouse, itemStack);
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
            // 播放拾取反馈音效
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), 
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 
                ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f);
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

    private static long getLastTick(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return customData.copyTag().getLong("lastTick");
        }
        return 0;
    }

    private static void setLastTick(ItemStack stack, long tick) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong("lastTick", tick);
        });
    }
}

