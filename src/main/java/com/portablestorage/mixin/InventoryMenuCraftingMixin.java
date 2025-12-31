package com.portablestorage.mixin;

import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseUtils;
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
        int[] extraIndices = {2, 5, 6, 7, 8};
        
        int[][] positions = {
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y},
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 18},
            {WarehouseConstants.CRAFT_3X3_X, WarehouseConstants.CRAFT_3X3_Y + 2 * 18},
            {WarehouseConstants.CRAFT_3X3_X + 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18},
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18}
        };

        for (int i = 0; i < extraIndices.length; i++) {
            final int idx = extraIndices[i];
            this.addSlot(new Slot(this.craftSlots, idx, positions[i][0], positions[i][1]) {
                @Override
                public boolean isActive() {
                    return WarehouseUtils.is3x3Enabled(owner);
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return WarehouseUtils.is3x3Enabled(owner);
                }
            });
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleCraftingQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return;

        if (WarehouseUtils.is3x3Enabled(player)) {
            if (slot instanceof ResultSlot || slot.container == this.craftSlots) {
                ItemStack stackInSlot = slot.getItem();
                ItemStack resultStack = stackInSlot.copy();

                int invStart = -1;
                int invEnd = -1;
                for (int i = 0; i < this.slots.size(); i++) {
                    Slot s = this.slots.get(i);
                    if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
                        if (invStart == -1) invStart = i;
                        invEnd = i + 1;
                    }
                }

                if (slot instanceof ResultSlot) {
                    if (invStart != -1) {
                        while (slot.hasItem()) {
                            ItemStack currentResult = slot.getItem();
                            ItemStack resultCopy = currentResult.copy();
                            currentResult.getItem().onCraftedBy(currentResult, player.level(), player);
                            if (!this.moveItemStackTo(currentResult, invStart, invEnd, true)) {
                                break;
                            }
                            slot.onQuickCraft(currentResult, resultCopy);
                            slot.onTake(player, currentResult);
                            if (currentResult.getCount() == resultCopy.getCount()) {
                                break;
                            }
                        }
                    }
                } else {
                    if (invStart != -1) {
                        if (!this.moveItemStackTo(stackInSlot, invStart, invEnd, false)) {
                            cir.setReturnValue(ItemStack.EMPTY);
                            return;
                        }
                    }
                    slot.onQuickCraft(stackInSlot, resultStack);
                    slot.setChanged();
                    this.slotsChanged(this.craftSlots);
                }
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }
}
