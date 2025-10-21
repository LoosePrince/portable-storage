package com.portable.storage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.block.TempBedBlock;
import com.portable.storage.net.ServerNetworkingHandlers;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 拦截玩家重生点设置，防止临时床设置重生点
 */
@Mixin(ServerPlayerEntity.class)
public class PlayerEntitySpawnMixin {

    @Inject(method = "setSpawnPoint", at = @At("HEAD"), cancellable = true)
    private void portableStorage$preventTempBedSpawnPoint(RegistryKey<World> dimension, BlockPos pos, float angle, boolean forced, boolean sendMessage, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;

        if (pos == null || dimension == null) {
            return;
        }

        ServerWorld world = self.getServer().getWorld(dimension);
        if (world == null) {
            return;
        }

        // 位置命中我们的临时床（通过记录表或方块类型判断）
        if (ServerNetworkingHandlers.isTempBed(pos) || world.getBlockState(pos).getBlock() instanceof TempBedBlock) {
            ci.cancel();
        }
    }
}
