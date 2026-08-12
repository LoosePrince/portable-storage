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
 * Prevents IndexOutOfBoundsException or container size desyncs during client container synchronization.
 * Only injects warehouse slots into adapted menus, leaving custom modded menus (like Backpacks) completely untouched.
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

    @Inject(method = "setRemoteSlot", at = @At("HEAD"))
    private void beforeSetRemoteSlot(int slotIndex, ItemStack stack, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (WarehouseMenuHandler.isAdaptedMenu(menu) && slotIndex >= menu.slots.size()) {
            WarehouseMenuHandler.injectWarehouseSlots(menu, Minecraft.getInstance().player);
        }
    }
}