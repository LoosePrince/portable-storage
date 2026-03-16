package com.portablestorage.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    private static final String KEY_FLAG = "portablestorage_is_warehouse_key";
    private static final String KEY_OWNER = "portablestorage_owner_uuid";

    // 标记是否曾经跌入过虚空，用于控制脱离虚空后的悬停逻辑
    private boolean portablestorage$wasInVoid = false;

    private boolean isWarehouseKey() {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return false;
        CompoundTag tag = customData.copyTag();
        return tag != null && tag.getBoolean(KEY_FLAG).orElse(false);
    }

    private UUID getWarehouseOwner() {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null)
            return null;
        CompoundTag tag = customData.copyTag();
        if (tag == null)
            return null;
        String ownerStr = tag.getString(KEY_OWNER).orElse("");
        if (ownerStr.isEmpty())
            return null;
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        if (!isWarehouseKey())
            return;
        UUID owner = getWarehouseOwner();
        if (owner == null)
            return;
        if (!owner.equals(player.getUUID())) {
            // 非拥有者玩家无法拾取该钥匙
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (!isWarehouseKey())
            return;
        ItemEntity self = (ItemEntity) (Object) this;
        Level level = self.level();
        if (level.isClientSide())
            return;

        double y = self.getY();
        int minY = ((LevelHeightAccessor) level).getMinY();

        // 虚空保护：低于世界最小构建高度时持续向上缓慢漂浮
        if (y < (double) minY) {
            portablestorage$wasInVoid = true;
            var velocity = self.getDeltaMovement();
            double newY = Math.max(velocity.y, 0.1D);
            self.setDeltaMovement(velocity.x, newY, velocity.z);
            // 轻微上推，避免数值一直向负无穷
            double clampY = Math.max(y, minY - 64.0D);
            if (y < clampY) {
                self.setPos(self.getX(), clampY, self.getZ());
            }
        } else if (portablestorage$wasInVoid) {
            // 已经从虚空中漂浮回安全高度后，锁定在边界附近并关闭重力，防止再次下落
            self.setNoGravity(true);
            self.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (y < (double) (minY + 1)) {
                self.setPos(self.getX(), (double) (minY + 1), self.getZ());
            }
        }
    }
}
