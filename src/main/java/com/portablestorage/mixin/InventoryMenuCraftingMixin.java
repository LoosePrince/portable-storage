package com.portablestorage.mixin;

import com.portablestorage.handler.WarehouseMenuHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InventoryMenu.class, priority = 1000)
public abstract class InventoryMenuCraftingMixin extends AbstractContainerMenu {

    @Shadow public abstract CraftingContainer getCraftSlots();

    protected InventoryMenuCraftingMixin(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addExtraCraftingSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        WarehouseMenuHandler.injectCraftingSlots(this, this.getCraftSlots(), owner);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleCraftingQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = WarehouseMenuHandler.handleCraftingQuickMove(this, this.slots, this.getCraftSlots(), player, index);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
