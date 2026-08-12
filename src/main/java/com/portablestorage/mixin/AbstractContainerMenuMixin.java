package com.portablestorage.mixin;

import com.portablestorage.handler.WarehouseInteractionHandler;
import com.portablestorage.handler.WarehouseMenuHandler;
import com.portablestorage.util.FakePlayerUtils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
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

    /**
     * Prevents Vanilla's broadcastChanges() from sending ClientboundContainerSetSlotPacket for UpgradeSlots or PlayerWarehouse slots.
     * Since custom warehouse and upgrade syncing is handled via Portable Storage's own packets (S2CWarehouseSnapshotPayload),
     * suppressing Vanilla's index-based slot packets prevents item bleed-through into crafting or Trinket slots when indices shift.
     */
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void suppressVanillaSlotPacketsForWarehouseSlots(CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot instanceof com.portablestorage.upgrade.UpgradeSlot || slot.container instanceof com.portablestorage.component.PlayerWarehouse) {
                menu.setRemoteSlot(i, slot.getItem().copy());
            }
        }
    }

    private static boolean isQuickMove(Object clickType) {
        return clickType != null && "QUICK_MOVE".equals(clickType.toString());
    }
}