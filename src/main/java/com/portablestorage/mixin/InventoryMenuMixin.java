package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    @Shadow @Final private Player owner;

    protected InventoryMenuMixin(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addWarehouseSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(owner.level()).getWarehouse(owner.getUUID());

        int startX = 8;
        int startY = 191; 
        
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(warehouse, col + row * 9, startX + col * 18, startY + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return true; }

                    @Override
                    public boolean isActive() {
                        return !owner.getAbilities().instabuild;
                    }
                });
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 46 && slotId < 100 && !player.getAbilities().instabuild) {
            PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
            ItemStack cursorStack = this.getCarried();

            if (!cursorStack.isEmpty()) {
                warehouse.addItem(cursorStack);
                this.setCarried(ItemStack.EMPTY);
            } else {
                int amount = (button == 1) ? 1 : 64;
                ItemStack taken = warehouse.removeItem(slotId - 46, amount);
                this.setCarried(taken);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (player.getAbilities().instabuild) return;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return;

        ItemStack stackInSlot = slot.getItem();
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());

        if (index >= 46 && index < 100) {
            long realCount = warehouse.getRealCount(index - 46);
            int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
            ItemStack resultStack = stackInSlot.copyWithCount(toTake);
            if (!this.moveItemStackTo(resultStack, 9, 45, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            warehouse.removeItem(index - 46, toTake - resultStack.getCount());
            cir.setReturnValue(ItemStack.EMPTY); 
        } else if (index >= 9 && index < 45) {
            warehouse.addItem(stackInSlot.copy());
            slot.set(ItemStack.EMPTY);
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
