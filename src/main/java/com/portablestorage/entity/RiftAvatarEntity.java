package com.portablestorage.entity;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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
        if (ownerId == null) {
            return null;
        }
        if (this.level().isClientSide()) {
            return null;
        }
        return this.level().getServer().getPlayerList().getPlayer(ownerId);
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
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("OwnerId").ifPresent(owner -> {
            try {
                this.ownerId = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.ownerId != null) {
            output.putString("OwnerId", this.ownerId.toString());
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.tickCount % 20 == 0) {
            this.setHealth(this.getMaxHealth());
        }
        super.tick();
    }
}
