package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.WarehouseComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
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
        WarehouseComponent warehouse = ModComponents.WAREHOUSE.get(owner);
        if (!(warehouse instanceof Container warehouseContainer)) return;

        int startX = 8;
        int startY = 178; 
        
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(warehouseContainer, col + row * 9, startX + col * 18, startY + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return true; 
                    }

                    @Override
                    public boolean isActive() {
                        return !owner.getAbilities().instabuild;
                    }
                });
            }
        }
    }

    // 彻底接管点击逻辑，防止原版逻辑导致物品复制
    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        // 仅处理生存模式且点击仓库槽位的情况 (46-99)
        if (player.getAbilities().instabuild || slotIndex < 46 || slotIndex >= 100) {
            super.clicked(slotIndex, button, clickType, player);
            return;
        }

        WarehouseComponent warehouse = ModComponents.WAREHOUSE.get(player);
        ItemStack carried = this.getCarried();
        int warehouseSlotIndex = slotIndex - 46;

        // 处理普通点击 (左键/右键)
        if (clickType == ClickType.PICKUP) {
            if (!carried.isEmpty()) {
                // 1. 存入逻辑：如果手中拿着东西，点击仓库直接存入手中物品
                warehouse.addItem(carried.copy());
                this.setCarried(ItemStack.EMPTY);
            } else {
                // 2. 取出逻辑：如果手中没东西，点击仓库取出物品
                long realCount = warehouse.getRealCount(warehouseSlotIndex);
                if (realCount > 0) {
                    ItemStack prototype = warehouse.getViewSlot(warehouseSlotIndex);
                    // 左键拿一组(64)，右键拿1个
                    int toTake = (button == 1) ? 1 : (int)Math.min(realCount, prototype.getMaxStackSize());
                    
                    ItemStack taken = prototype.copyWithCount(toTake);
                    warehouse.removeItem(warehouseSlotIndex, toTake);
                    this.setCarried(taken);
                }
            }
            return; // 关键：不再调用 super.clicked，彻底阻止原版逻辑
        }

        // 保持 Shift-点击的兼容性，它会调用 quickMoveStack
        super.clicked(slotIndex, button, clickType, player);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
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
