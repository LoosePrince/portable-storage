package com.portablestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.handler.WarehouseMenuHandler;
import com.portablestorage.upgrade.BedUpgrade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "initMenu", at = @At("HEAD"))
    private void onInitMenu(AbstractContainerMenu menu, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        // 排除创造模式背包菜单
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu && player.getAbilities().instabuild)
            return;

        WarehouseMenuHandler.injectWarehouseSlots(menu, player);
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void onSetRespawnPosition(ServerPlayer.RespawnConfig config, boolean sendMessage, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.isSleeping() && !config.forced()) {
            PlayerWarehouse warehouse = ModComponents.getWarehouse(
                    ((net.minecraft.server.level.ServerLevel) player.level()).getServer(), player.getUUID());
            if (warehouse != null && warehouse.isEnabled() && !warehouse.getUpgrade(BedUpgrade.ID).isEmpty()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("TAIL"))
    private void onWakeUp(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        BedUpgrade.cleanupTempBed(player);
    }
}
