package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.WarehouseComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
        WarehouseComponent warehouse = ModComponents.WAREHOUSE.get(owner);
        
        // 同步 Screen 的修改：
        // 间隔 4px, 背景 startY = 166 + 4 = 170
        // 槽位对齐修正为 +8, 最终 startY = 170 + 8 = 178
        // 槽位横向对齐修正为 +8
        int startX = 8;
        int startY = 178; 
        
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(warehouse, col + row * 9, startX + col * 18, startY + row * 18));
            }
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack copy = stackInSlot.copy();

        // 仓库槽位索引从 46 开始 (0-45 是原版槽位)
        if (index >= 46 && index < 100) {
            if (!this.moveItemStackTo(stackInSlot, 9, 45, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        } else if (index >= 9 && index < 45) {
            if (!this.moveItemStackTo(stackInSlot, 46, 100, false)) {
                return; 
            }
        } else {
            return;
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stackInSlot.getCount() == copy.getCount()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        slot.onTake(player, stackInSlot);
        cir.setReturnValue(copy);
    }
}
