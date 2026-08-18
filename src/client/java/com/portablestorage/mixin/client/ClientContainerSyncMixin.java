package com.portablestorage.mixin.client;

import java.util.List;

import com.portablestorage.handler.WarehouseMenuHandler;
import com.portablestorage.util.CompatibilityDebug;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class ClientContainerSyncMixin {

    @Inject(method = "initializeContents", at = @At("HEAD"))
    private void beforeInitializeContents(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (WarehouseMenuHandler.isAdaptedMenu(menu) && items.size() > menu.slots.size()) {
            CompatibilityDebug.log("sync", () -> "initializeContents arrived before warehouse slot injection; payloadSlots="
                    + items.size() + "; menuSlots=" + menu.slots.size() + "; menu=" + menu.getClass().getName());
            WarehouseMenuHandler.injectWarehouseSlots(menu, Minecraft.getInstance().player);
        }
    }

    @Inject(method = "setRemoteSlot", at = @At("HEAD"), cancellable = true)
    private void beforeSetRemoteSlot(int slotIndex, ItemStack stack, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (WarehouseMenuHandler.isAdaptedMenu(menu) && slotIndex >= menu.slots.size()) {
            CompatibilityDebug.log("sync", () -> "setRemoteSlot arrived before warehouse slot injection; slotIndex="
                    + slotIndex + "; menuSlots=" + menu.slots.size() + "; menu=" + menu.getClass().getName());
            WarehouseMenuHandler.injectWarehouseSlots(menu, Minecraft.getInstance().player);
        }
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            ci.cancel();
        }
    }
}