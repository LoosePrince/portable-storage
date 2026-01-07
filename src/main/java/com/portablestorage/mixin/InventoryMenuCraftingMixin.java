package com.portablestorage.mixin;

import com.portablestorage.handler.WarehouseMenuHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InventoryMenu.class, priority = 1000)
public abstract class InventoryMenuCraftingMixin extends AbstractContainerMenu {

    @Shadow @Final @Mutable private CraftingContainer craftSlots;

    protected InventoryMenuCraftingMixin(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/inventory/TransientCraftingContainer"))
    private TransientCraftingContainer redirectCraftingContainer(AbstractContainerMenu menu, int width, int height, Inventory inventory, boolean active, Player owner) {
        return new TransientCraftingContainer(menu, 3, 3);
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/inventory/ResultSlot"))
    private ResultSlot redirectResultSlot(Player player, CraftingContainer craftingContainer, net.minecraft.world.Container resultContainer, int slot, int x, int y, Inventory inventory, boolean active, Player owner) {
        return new ResultSlot(player, this.craftSlots, (ResultContainer) resultContainer, slot, x, y);
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/inventory/Slot", ordinal = 0))
    private Slot redirectCraftingSlots(net.minecraft.world.Container container, int index, int x, int y, Inventory inventory, boolean active, Player owner) {
        if (container instanceof CraftingContainer) {
            int newIndex = switch (index) {
                case 2 -> 3;
                case 3 -> 4;
                default -> index;
            };
            return new Slot(container, newIndex, x, y);
        }
        return new Slot(container, index, x, y);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addExtraCraftingSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        WarehouseMenuHandler.injectCraftingSlots(this, this.craftSlots, owner);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleCraftingQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = WarehouseMenuHandler.handleCraftingQuickMove(this, this.slots, this.craftSlots, player, index);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
