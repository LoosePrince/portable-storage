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

        if (index == 0) { // 合成结果
            // 补全工作台级别的连续合成逻辑
            while (slot.hasItem()) {
                ItemStack currentResult = slot.getItem();
                ItemStack resultCopy = currentResult.copy();
                
                currentResult.getItem().onCraftedBy(currentResult, player.level(), player);
                
                // 尝试移动到背包 (9-45)
                if (!this.moveItemStackTo(currentResult, 9, 45, true)) {
                    break;
                }
                
                slot.onQuickCraft(currentResult, resultCopy);
                slot.onTake(player, currentResult);
                
                // 如果数量没变，说明背包满了或材料没了，跳出循环
                if (currentResult.getCount() == resultCopy.getCount()) {
                    break;
                }
            }
            cir.setReturnValue(ItemStack.EMPTY);
        } else if (index >= 1 && index <= 4 || (index >= 46 && index <= 50)) { // 合成槽位
            if (!this.moveItemStackTo(stackInSlot, 9, 45, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            slot.setChanged();
            cir.setReturnValue(resultStack);
        }
    }
}

