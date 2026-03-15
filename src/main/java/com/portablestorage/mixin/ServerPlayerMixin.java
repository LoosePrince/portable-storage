package com.portablestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        // 某些情况下（例如客户端异常发包）config 可能为 null，此时直接交给原版逻辑处理
        if (config == null) {
            return;
        }

        ServerPlayer player = (ServerPlayer) (Object) this;
        // 设置重生点可能在 isSleeping() 变为 true 之前触发，故仅用“是否在临时床列表”判断
        if (!config.forced() && BedUpgrade.PLAYER_TEMP_BEDS.containsKey(player.getUUID())) {
            ci.cancel();
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("TAIL"))
    private void onWakeUp(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        BedUpgrade.cleanupTempBed(player);
    }
}
