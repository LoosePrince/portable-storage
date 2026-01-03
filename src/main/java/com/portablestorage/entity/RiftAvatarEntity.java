package com.portablestorage.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.UUID;

public class RiftAvatarEntity extends LivingEntity {
    private UUID ownerId;

    public RiftAvatarEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(false);
        this.setSilent(true);
        this.setNoGravity(true);
        this.setCustomNameVisible(true);
        this.noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    private ServerPlayer getOwnerPlayer() {
        if (ownerId == null) return null;
        if (this.level().isClientSide) return null;
        return this.level().getServer().getPlayerList().getPlayer(ownerId);
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return net.minecraft.world.entity.HumanoidArm.RIGHT;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        ServerPlayer owner = getOwnerPlayer();
        if (owner != null) {
            owner.hurt(source, amount);
            return true;
        }
        return false;
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, net.minecraft.world.entity.Entity source) {
        ServerPlayer owner = getOwnerPlayer();
        if (owner != null) {
            owner.addEffect(new MobEffectInstance(effect));
            return true;
        }
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID("OwnerId")) {
            this.ownerId = compound.getUUID("OwnerId");
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.ownerId != null) {
            compound.putUUID("OwnerId", this.ownerId);
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            this.setHealth(this.getMaxHealth());
        }
        super.tick();
    }
}

