package com.portablestorage.mixin;

import com.portablestorage.handler.WarehouseInteractionHandler;
import com.portablestorage.handler.WarehouseMenuHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void handleWarehouseClicks(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        // Automatically inject warehouse slots if not present
        WarehouseMenuHandler.injectWarehouseSlots((AbstractContainerMenu) (Object) this, player);

        // Handle Shift+Click (Quick Move) globally
        if (clickType == ClickType.QUICK_MOVE) {
            if (WarehouseMenuHandler.handleQuickMove((AbstractContainerMenu) (Object) this, player, slotId) != null) {
                ci.cancel();
                return;
            }
        }

        if (WarehouseInteractionHandler.handleClicked((AbstractContainerMenu) (Object) this, slotId, button, clickType, player)) {
            ci.cancel();
        }
    }
}
