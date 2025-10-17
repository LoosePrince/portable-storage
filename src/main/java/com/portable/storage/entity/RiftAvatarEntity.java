package com.portable.storage.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public class RiftAvatarEntity extends PathAwareEntity {
    private UUID ownerId;

    public RiftAvatarEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.noClip = false; // 允许碰撞
        this.setInvulnerable(false);
        this.setSilent(true);
        this.setNoGravity(false); // 启用重力
        this.setAiDisabled(false); // 允许物理与基本移动更新
        this.setCustomNameVisible(true);
    }

    public static RiftAvatarEntity spawn(ServerWorld world, BlockPos pos, UUID owner) {
        RiftAvatarEntity e = new RiftAvatarEntity(ModEntities.RIFT_AVATAR, world);
        e.ownerId = owner;
        e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        world.spawnEntity(e);
        return e;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes();
    }

    public void setOwner(UUID owner) {
        this.ownerId = owner;
    }

    public UUID getOwner() {
        return ownerId;
    }

    private ServerPlayerEntity getOwnerPlayer() {
        if (ownerId == null) return null;
        if (!(this.getWorld() instanceof ServerWorld sw)) return null;
        return sw.getServer().getPlayerManager().getPlayer(ownerId);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        ServerPlayerEntity owner = getOwnerPlayer();
        if (owner != null) {
            owner.damage(source, amount);
            // 抑制自身扣血
            return false;
        }
        return false;
    }

    @Override
    public boolean addStatusEffect(StatusEffectInstance effect, Entity source) {
        ServerPlayerEntity owner = getOwnerPlayer();
        if (owner != null) {
            owner.addStatusEffect(new StatusEffectInstance(effect));
            return false;
        }
        return false;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        // 允许外部尝试对其施加效果，以便我们在 addStatusEffect 中拦截并转发
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        // 不对任意来源免疫，进入 damage() 以便转发
        return false;
    }

    @Override
    public boolean isPushable() { return true; } // 可被推动

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("owner")) this.ownerId = nbt.getUuid("owner");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerId != null) nbt.putUuid("owner", ownerId);
    }

    @Override
    public void tick() {
        super.tick();
        this.setHealth(this.getMaxHealth());
    }

    @Override
    protected void initGoals() {
        // no AI
    }
}


