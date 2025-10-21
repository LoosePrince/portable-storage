package com.portable.storage.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portable.storage.block.ModBlocks;
import com.portable.storage.blockentity.BoundBarrelBlockEntity;

import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(BlockItem.class)
public abstract class BarrelBlockItemMixin {
    @Inject(method = "postPlacement", at = @At("TAIL"))
    private void portableStorage$replaceBoundBarrel(BlockPos pos, World world, PlayerEntity player, ItemStack stack, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (stack.getItem() != Items.BARREL) return;
            
            // 检查是否是绑定木桶
            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData == null) return;
            
            NbtCompound nbt = customData.copyNbt();
            UUID uuid = null;
            String name = null;
            
            if (nbt.containsUuid("ps_owner_uuid")) {
                uuid = nbt.getUuid("ps_owner_uuid");
            } else if (nbt.contains("ps_owner_uuid_most")) {
                uuid = new UUID(nbt.getLong("ps_owner_uuid_most"), nbt.getLong("ps_owner_uuid_least"));
            }
            
            if (nbt.contains("ps_owner_name")) {
                name = nbt.getString("ps_owner_name");
            }
            
            // 如果有绑定信息，替换为自定义方块
            if (uuid != null) {
                // 替换方块
                world.setBlockState(pos, ModBlocks.BOUND_BARREL.getDefaultState(), 3);
                
                // 设置方块实体数据
                if (world.getBlockEntity(pos) instanceof BoundBarrelBlockEntity boundBarrel) {
                    boundBarrel.setOwner(uuid, name);
                }
            }
        } catch (Throwable ignored) {}
    }
}


