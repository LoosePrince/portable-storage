package com.portablestorage.upgrade;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class ExperienceUpgrade extends UpgradeType {
    public static final ResourceLocation ID = PortableStorage.id("experience");
    private static final String TAG_MAINTAIN = "MaintainLevel";
    private static final String TAG_STEP_INDEX = "StepIndex";
    private static final int[] STEPS = {1, 5, 10, 100};

    public ExperienceUpgrade() {
        super(ID, null, stack -> stack.is(Items.EXPERIENCE_BOTTLE));
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.experience.desc").withStyle(ChatFormatting.GRAY));
        
        CompoundTag tag = stack.getTag();
        int stepIndex = 0;
        boolean maintain = false;
        if (tag != null) {
            stepIndex = tag.getInt(TAG_STEP_INDEX);
            maintain = tag.getBoolean(TAG_MAINTAIN);
        }
        
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.experience.access_level", 
            Component.literal(String.valueOf(STEPS[stepIndex])).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.YELLOW));
        
        tooltips.add(Component.translatable("upgrade.portablestorage.experience.maintain", 
            Component.translatable(maintain ? "gui.portablestorage.on" : "gui.portablestorage.off")
            .withStyle(maintain ? ChatFormatting.GREEN : ChatFormatting.RED)
        ).withStyle(ChatFormatting.YELLOW));
        
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.experience.toggle_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltips.add(Component.translatable("upgrade.portablestorage.experience.maintain_hint").withStyle(ChatFormatting.DARK_GRAY));
        
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrCreateTag();
            int current = tag.getInt(TAG_STEP_INDEX);
            tag.putInt(TAG_STEP_INDEX, (current + 1) % STEPS.length);
            warehouse.markDirty();
            
            int step = getStep(stack);
            player.displayClientMessage(Component.translatable("upgrade.portablestorage.experience.access_level", 
                Component.literal(String.valueOf(step))
            ), true);
        }
    }

    @Override
    public void onMiddleClick(PlayerWarehouse warehouse, Player player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrCreateTag();
            boolean current = tag.getBoolean(TAG_MAINTAIN);
            tag.putBoolean(TAG_MAINTAIN, !current);
            if (!current) {
                tag.putInt("TargetLevel", player.experienceLevel);
            } else {
                tag.remove("TargetLevel");
            }
            warehouse.markDirty();
            
            boolean maintain = tag.getBoolean(TAG_MAINTAIN);
            player.displayClientMessage(Component.translatable("upgrade.portablestorage.experience.maintain", 
                Component.translatable(maintain ? "gui.portablestorage.on" : "gui.portablestorage.off")
            ), true);
        }
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, ServerPlayer player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.getBoolean(TAG_MAINTAIN)) {
            // 等级维持逻辑
            // 如果玩家当前经验值有变动，尝试平衡
            // 如果玩家等级超过了当前等级且有余量经验，存入；如果少了且仓库有，取出。
            if (!tag.contains("TargetLevel")) {
                tag.putInt("TargetLevel", player.experienceLevel);
                warehouse.markDirty();
                return;
            }
            
            int target = tag.getInt("TargetLevel");
            syncExperience(player, warehouse, target);
        }
    }

    public static void syncExperience(ServerPlayer player, PlayerWarehouse warehouse, int targetLevel) {
        long currentTotalXp = getTotalExperience(player);
        long targetTotalXp = getExperienceForLevel(targetLevel);
        
        if (currentTotalXp > targetTotalXp) {
            // 存入多余的
            long toStore = currentTotalXp - targetTotalXp;
            warehouse.addExperience(toStore);
            addExperience(player, (int) -toStore);
        } else if (currentTotalXp < targetTotalXp) {
            // 取出缺少的
            long toTake = Math.min(targetTotalXp - currentTotalXp, warehouse.getExperience());
            if (toTake > 0) {
                warehouse.addExperience(-toTake);
                addExperience(player, (int) toTake);
            }
        }
    }

    // ========== 经验计算工具 ==========

    public static long getExperienceForLevel(int level) {
        if (level <= 16) return (long) level * level + 6L * level;
        if (level <= 31) return (long) (2.5 * level * level - 40.5 * level + 360);
        return (long) (4.5 * level * level - 162.5 * level + 2220);
    }

    public static long getTotalExperience(Player player) {
        return getExperienceForLevel(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    public static void addExperience(Player player, int amount) {
        player.giveExperiencePoints(amount);
    }
    
    public static int getStep(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            return STEPS[tag.getInt(TAG_STEP_INDEX)];
        }
        return STEPS[0];
    }
}

