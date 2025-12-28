package com.portablestorage.mixin;

import com.portablestorage.config.ModConfig;
import com.portablestorage.util.WarehouseConstants;
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

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuCraftingMixin extends AbstractContainerMenu {

    @Shadow @Final @Mutable private CraftingContainer craftSlots;

    protected InventoryMenuCraftingMixin(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(Lnet/minecraft/world/inventory/AbstractContainerMenu;II)Lnet/minecraft/world/inventory/TransientCraftingContainer;"))
    private TransientCraftingContainer redirectCraftingContainer(AbstractContainerMenu menu, int width, int height) {
        if (ModConfig.enable3x3Crafting) {
            return new TransientCraftingContainer(menu, 3, 3);
        }
        return new TransientCraftingContainer(menu, width, height);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addExtraCraftingSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        if (ModConfig.enable3x3Crafting) {
            for (int i = 0; i < WarehouseConstants.EXTRA_CRAFTING_SLOTS; i++) {
                this.addSlot(new Slot(this.craftSlots, WarehouseConstants.VANILLA_CRAFTING_INPUT_COUNT + i, 0, 0));
            }
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleCraftingQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (!ModConfig.enable3x3Crafting) return;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return;
        ItemStack stackInSlot = slot.getItem();
        ItemStack resultStack = stackInSlot.copy();

        // 查找玩家背包范围 (Main + Hotbar)
        int invStart = -1;
        int invEnd = -1;
        for (int i = 0; i < this.slots.size(); i++) {
            Slot s = this.slots.get(i);
            if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
                if (invStart == -1) invStart = i;
                invEnd = i + 1;
            }
        }

        if (slot instanceof ResultSlot) { // 合成结果
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
            cir.setReturnValue(ItemStack.EMPTY);
        } else if (slot.container == this.craftSlots) { // 合成槽位
            if (invStart != -1) {
                if (!this.moveItemStackTo(stackInSlot, invStart, invEnd, false)) {
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
            }
            slot.onQuickCraft(stackInSlot, resultStack);
            slot.setChanged();
            this.slotsChanged(this.craftSlots); // 手动触发合成结果更新
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}

