package com.portablestorage.mixin;

import com.portablestorage.handler.WarehouseInteractionHandler;
import com.portablestorage.handler.WarehouseMenuHandler;
import com.portablestorage.util.FakePlayerUtils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void handleWarehouseClicks(int slotId, int button, ContainerInput clickType, Player player, CallbackInfo ci) {
        if (FakePlayerUtils.isFakePlayer(player))
            return;

        WarehouseMenuHandler.injectWarehouseSlots((AbstractContainerMenu) (Object) this, player);

        if (isQuickMove(clickType)) {
            if (WarehouseMenuHandler.handleQuickMove((AbstractContainerMenu) (Object) this, player, slotId) != null) {
                ci.cancel();
                return;
            }
        }

        if (WarehouseInteractionHandler.handleClicked((AbstractContainerMenu) (Object) this, slotId, button, clickType, player)) {
            ci.cancel();
        }
    }

    private static boolean isQuickMove(Object clickType) {
        return clickType != null && "QUICK_MOVE".equals(clickType.toString());
    }
}