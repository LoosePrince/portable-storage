package com.portablestorage.mixin.client;

import java.util.List;

import com.portablestorage.handler.WarehouseMenuHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps client-side menu slots in step with server-side warehouse injection.
 *
 * A menu can receive its first content packet before the client screen has
 * initialized and injected the warehouse slots. Try to inject them before
 * indexing the packet, and ignore an index that is still outside the menu.
 */
@Mixin(AbstractContainerMenu.class)
public class ClientContainerSyncMixin {

    @Inject(method = "initializeContents", at = @At("HEAD"))
    private void beforeInitializeContents(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (WarehouseMenuHandler.isAdaptedMenu(menu) && items.size() > menu.slots.size()) {
            WarehouseMenuHandler.injectWarehouseSlots(menu, Minecraft.getInstance().player);
        }
    }

    @Inject(method = "setRemoteSlot", at = @At("HEAD"), cancellable = true)
    private void beforeSetRemoteSlot(int slotIndex, ItemStack stack, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (WarehouseMenuHandler.isAdaptedMenu(menu) && slotIndex >= menu.slots.size()) {
            WarehouseMenuHandler.injectWarehouseSlots(menu, Minecraft.getInstance().player);
        }
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            ci.cancel();
        }
    }
}
