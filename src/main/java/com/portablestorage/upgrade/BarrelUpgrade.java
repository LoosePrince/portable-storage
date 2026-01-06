package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * 木桶升级
 * 放入木桶后变为绑定木桶，可作为远程存取点
 */
public class BarrelUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("barrel");

    public BarrelUpgrade() {
        super(
            ID,
            null,
            stack -> stack.is(Items.BARREL) || stack.is(ModItems.BOUND_BARREL)
        );
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.BARREL);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.barrel.desc").withStyle(ChatFormatting.GRAY));
        
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().hasUUID("owner")) {
            String ownerName = customData.copyTag().getString("ownerName");
            tooltips.add(Component.literal(" "));
            tooltips.add(Component.translatable("upgrade.portablestorage.barrel.bound_to", ownerName).withStyle(ChatFormatting.YELLOW));
        }
        
        return tooltips;
    }

    @Override
    public void onInstall(PlayerWarehouse warehouse, ItemStack stack) {
        if (stack.is(Items.BARREL)) {
            // 将普通木桶替换为绑定木桶
            ItemStack boundStack = new ItemStack(ModItems.BOUND_BARREL, stack.getCount());
            
            // 绑定玩家信息
            CustomData.update(DataComponents.CUSTOM_DATA, boundStack, tag -> {
                tag.putUUID("owner", warehouse.getOwnerUuid());
                
                // 尝试获取玩家名字（如果可能）
                // 在 Fabric 中我们可以通过 Server 来查找
                String playerName = "Player";
                // 我们在 upgradeStorage 中存入这个物品，serverTick 会纠正名字
                tag.putString("ownerName", playerName);
            });
            
            // 替换仓库中的物品
            warehouse.getUpgradeStorage().put(ID, boundStack);
            warehouse.markDirty();
        }
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, net.minecraft.server.level.ServerPlayer player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        if (stack.is(ModItems.BOUND_BARREL)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                var tag = customData.copyTag();
                if (tag.hasUUID("owner")) {
                    java.util.UUID ownerUuid = tag.getUUID("owner");
                    if (ownerUuid.equals(player.getUUID())) {
                        String currentName = tag.getString("ownerName");
                        if ("Player".equals(currentName) || !player.getScoreboardName().equals(currentName)) {
                            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
                                t.putString("ownerName", player.getScoreboardName());
                            });
                            warehouse.markDirty();
                        }
                    }
                }
            }
        }
    }
}

