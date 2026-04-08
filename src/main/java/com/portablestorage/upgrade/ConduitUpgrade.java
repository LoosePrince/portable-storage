package com.portablestorage.upgrade;

import java.util.List;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public class ConduitUpgrade extends UpgradeType {
    public static final Identifier ID = PortableStorage.id("conduit");

    private static final String TAG_MODE = "ConduitMode";
    /** 模式：0 默认，1 攻击 */
    private static final int MODE_DEFAULT = 0;
    private static final int MODE_ATTACK = 1;

    /** 潮涌能量所需海晶系方块数 */
    private static final int BLOCKS_FOR_CONDUIT_POWER = 16;
    /** 攻击模式所需海晶系方块数 */
    private static final int BLOCKS_FOR_ATTACK = 42;

    private static final net.minecraft.world.item.Item[] CONDUIT_BLOCKS = {
            Items.PRISMARINE,
            Items.DARK_PRISMARINE,
            Items.PRISMARINE_BRICKS,
            Items.SEA_LANTERN
    };

    public ConduitUpgrade() {
        super(ID, null, stack -> stack.is(Items.CONDUIT));
    }

    @Override
    public ItemStack getIconStack() {
        return new ItemStack(Items.CONDUIT);
    }

    /**
     * 统计仓库中海晶石、暗海晶石、海晶石砖、海晶灯的总数量（共享组内）
     */
    public static long getConduitBlockCount(PlayerWarehouse warehouse) {
        long total = 0;
        for (net.minecraft.world.item.Item item : CONDUIT_BLOCKS) {
            total += warehouse.getLiveCount(new ItemStack(item));
        }
        return total;
    }

    private static int getMode(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null)
            return MODE_DEFAULT;
        int m = data.copyTag().getInt(TAG_MODE).orElse(MODE_DEFAULT);
        if (m != MODE_DEFAULT && m != MODE_ATTACK)
            return MODE_DEFAULT;
        return m;
    }

    private static void setMode(ItemStack stack, int mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_MODE, mode));
    }

    @Override
    public List<Component> getTooltip(PlayerWarehouse warehouse, ItemStack stack) {
        List<Component> tooltips = super.getTooltip(warehouse, stack);
        tooltips.add(Component.translatable("upgrade.portablestorage.conduit.desc").withStyle(ChatFormatting.GRAY));

        long count = getConduitBlockCount(warehouse);
        tooltips.add(Component.literal(" "));
        Component powerText = Component.translatable("upgrade.portablestorage.conduit.count_units",
                count, BLOCKS_FOR_CONDUIT_POWER)
                .withStyle(count >= BLOCKS_FOR_CONDUIT_POWER ? ChatFormatting.GREEN : ChatFormatting.RED);
        tooltips.add(Component.translatable("upgrade.portablestorage.conduit.conduit_power", powerText)
                .withStyle(ChatFormatting.YELLOW));

        Component attackText = Component.translatable("upgrade.portablestorage.conduit.count_units",
                count, BLOCKS_FOR_ATTACK)
                .withStyle(count >= BLOCKS_FOR_ATTACK ? ChatFormatting.GREEN : ChatFormatting.RED);
        tooltips.add(Component.translatable("upgrade.portablestorage.conduit.attack_mode", attackText)
                .withStyle(ChatFormatting.YELLOW));

        int mode = getMode(stack);
        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.conduit.mode",
                Component.translatable("upgrade.portablestorage.conduit.mode." + mode).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.YELLOW));

        tooltips.add(Component.literal(" "));
        tooltips.add(Component.translatable("upgrade.portablestorage.conduit.toggle_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        return tooltips;
    }

    @Override
    public void onRightClick(PlayerWarehouse warehouse, Player player) {
        ItemStack stack = warehouse.getUpgrade(ID);
        if (stack.isEmpty())
            return;

        int current = getMode(stack);
        long blockCount = getConduitBlockCount(warehouse);
        int next;
        if (current == MODE_DEFAULT) {
            next = (blockCount >= BLOCKS_FOR_ATTACK) ? MODE_ATTACK : MODE_DEFAULT;
        } else {
            next = MODE_DEFAULT;
        }
        setMode(stack, next);
        warehouse.markDirty();

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("upgrade.portablestorage.conduit.mode",
                            Component.translatable("upgrade.portablestorage.conduit.mode." + next)),
                    true);
        }
    }

    @Override
    public void serverTick(PlayerWarehouse warehouse, ServerPlayer player) {
        if (!ModConfig.enableConduitUpgrade)
            return;

        ItemStack stack = warehouse.getUpgrade(ID);
        if (stack.isEmpty())
            return;

        long blockCount = getConduitBlockCount(warehouse);
        int mode = getMode(stack);

        // 潮涌能量：≥16 且接触水/雨时每秒给予 13 秒潮涌能量
        if (blockCount >= BLOCKS_FOR_CONDUIT_POWER) {
            boolean inWaterOrRain = player.isInWater() || player.level().isRainingAt(player.blockPosition());
            if (inWaterOrRain && player.tickCount % 20 == 0) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.CONDUIT_POWER, 260, 0, true, true, true));
            }
        }

        // 攻击模式：≥42 方块时每 2 秒对 8 格内单个敌对目标造成 4 点玩家伤害并击退（排除猪灵、猪灵蛮兵、疣猪兽）
        if (mode == MODE_ATTACK && blockCount >= BLOCKS_FOR_ATTACK && player.tickCount % 40 == 0) {
            tryAttack(warehouse, player);
        }
    }

    @SuppressWarnings("deprecation")
    private static void tryAttack(PlayerWarehouse warehouse, ServerPlayer player) {
        net.minecraft.world.level.Level level = player.level();
        net.minecraft.world.phys.AABB aabb = net.minecraft.world.phys.AABB.ofSize(
                player.position(), 16, 16, 16);
        Player owner = level.getServer() != null
                ? level.getServer().getPlayerList().getPlayer(warehouse.getOwnerUuid())
                : null;
        if (owner == null)
            owner = player;

        net.minecraft.world.damagesource.DamageSource damageSource = level.damageSources()
                .playerAttack((net.minecraft.world.entity.player.Player) owner);

        List<net.minecraft.world.entity.LivingEntity> candidates = level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                aabb,
                e -> e != player
                        && e.isAlive()
                        && e.distanceTo(player) <= 8
                        && !(e instanceof net.minecraft.world.entity.player.Player)
                        && e instanceof net.minecraft.world.entity.monster.Monster
                        && e.getType() != net.minecraft.world.entity.EntityType.PIGLIN
                        && e.getType() != net.minecraft.world.entity.EntityType.PIGLIN_BRUTE
                        && e.getType() != net.minecraft.world.entity.EntityType.HOGLIN);

        net.minecraft.world.entity.LivingEntity target = null;
        double nearest = Double.MAX_VALUE;
        for (net.minecraft.world.entity.LivingEntity e : candidates) {
            double d = e.distanceToSqr(player);
            if (d < nearest) {
                nearest = d;
                target = e;
            }
        }
        if (target != null) {
            target.hurt(damageSource, 4f);
            target.knockback(0.4, player.getX() - target.getX(), player.getZ() - target.getZ());
        }
    }
}
