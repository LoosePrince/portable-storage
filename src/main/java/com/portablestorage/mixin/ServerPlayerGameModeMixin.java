package com.portablestorage.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.upgrade.PistonUpgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void onBlockLeftClick(BlockPos pos,
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action, Direction direction, int i,
            int j, CallbackInfo ci) {
        if (action == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            if (player.getMainHandItem().is(Items.PISTON) || player.getMainHandItem().is(Items.STICKY_PISTON)) {
                PlayerWarehouse warehouse = ModComponents.getWarehouse(((net.minecraft.server.level.ServerLevel) player.level()).getServer(),
                        player.getUUID());
                if (warehouse != null && warehouse.isEnabled() && !warehouse.getUpgrade(PistonUpgrade.ID).isEmpty()) {
                    BlockState state = player.level().getBlockState(pos);
                    BlockState rotated = state.rotate(Rotation.CLOCKWISE_90);
                    if (rotated != state) {
                        player.level().setBlockAndUpdate(pos, rotated);
                        ci.cancel();
                    }
                }
            }
        }
    }
}
