package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.upgrade.BedUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.portablestorage.handler.WarehouseMenuHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "initMenu", at = @At("HEAD"))
    private void onInitMenu(AbstractContainerMenu menu, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        // 排除创造模式背包菜单的槽位注入
        if (!(menu instanceof net.minecraft.world.inventory.InventoryMenu && player.getAbilities().instabuild)) {
            WarehouseMenuHandler.injectWarehouseSlots(menu, player);
        }

        // 打开菜单时主动刷新客户端仓库状态，避免箱子界面使用过期的客户端快照。
        ModComponents.get(player).syncForPlayer(player.getUUID());
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void onSetRespawnPosition(ResourceKey<Level> dimension, BlockPos pos, float angle, boolean forced, boolean sendMessage, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.isSleeping() && !forced) {
            PlayerWarehouse warehouse = ModComponents.getWarehouse(player.getServer(), player.getUUID());
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

