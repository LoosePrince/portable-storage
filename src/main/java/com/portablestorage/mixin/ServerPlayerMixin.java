package com.portablestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portablestorage.handler.WarehouseMenuHandler;
import com.portablestorage.upgrade.BedUpgrade;
import com.portablestorage.util.CompatibilityDebug;
import com.portablestorage.util.FakePlayerUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "initMenu", at = @At("HEAD"), cancellable = true)
    private void onInitMenu(AbstractContainerMenu menu, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (FakePlayerUtils.isFakePlayer(player)) {
            CompatibilityDebug.log("fake-player", () -> "skipped initMenu for " + player.getClass().getName());
            return;
        }

        WarehouseMenuHandler.injectWarehouseSlots(menu, player);
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void onSetRespawnPosition(ServerPlayer.RespawnConfig config, boolean sendMessage, CallbackInfo ci) {
        if (config == null) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        if (FakePlayerUtils.isFakePlayer(player))
            return;

        if (!config.forced() && BedUpgrade.PLAYER_TEMP_BEDS.containsKey(player.getUUID())) {
            ci.cancel();
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("TAIL"))
    private void onWakeUp(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (FakePlayerUtils.isFakePlayer(player))
            return;
        BedUpgrade.cleanupTempBed(player);
    }
}