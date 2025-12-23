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
import net.minecraft.world.Container;
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
        // 不再在这里直接返回，而是始终注入槽位，但在创造模式下禁用它们
        WarehouseComponent warehouse = ModComponents.WAREHOUSE.get(owner);
        if (!(warehouse instanceof Container warehouseContainer)) return;

        int startX = 8;
        int startY = 178; 
        
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                // 使用自定义 Slot 类，动态控制其激活状态
                this.addSlot(new Slot(warehouseContainer, col + row * 9, startX + col * 18, startY + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return true; 
                    }

                    @Override
                    public boolean isActive() {
                        // 动态判断：只有非创造模式下才激活这些槽位
                        // 使用 abilities.instabuild 是最安全的早期判断方式，因为它不会 NPE
                        return !owner.getAbilities().instabuild;
                    }
                });
            }
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        // 快速转移逻辑仅在非创造模式下生效
        if (player.getAbilities().instabuild) return;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return;

        ItemStack stackInSlot = slot.getItem();
        WarehouseComponent warehouse = ModComponents.WAREHOUSE.get(player);

        if (index >= 46 && index < 100) {
            long realCount = warehouse.getRealCount(index - 46);
            int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
            
            ItemStack resultStack = stackInSlot.copyWithCount(toTake);
            if (!this.moveItemStackTo(resultStack, 9, 45, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            
            int moved = toTake - resultStack.getCount();
            warehouse.removeItem(index - 46, moved);
            cir.setReturnValue(ItemStack.EMPTY); 
        } else if (index >= 9 && index < 45) {
            warehouse.addItem(stackInSlot.copy());
            slot.set(ItemStack.EMPTY);
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
