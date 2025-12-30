package com.portablestorage.mixin;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void handleWarehouseClicks(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (slotId < 0) return;
        
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotId >= menu.slots.size()) return;

        Slot slot = menu.slots.get(slotId);
        
        // 处理主仓库槽位
        if (slot.container instanceof PlayerWarehouse warehouse) {
            if (!warehouse.isEnabled()) return;
            int warehouseStart = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container == warehouse) {
                    warehouseStart = i;
                    break;
                }
            }
            if (warehouseStart == -1) return;

            if (!player.getAbilities().instabuild) {
                if (clickType == ClickType.QUICK_MOVE) {
                    ci.cancel();
                    return;
                }

                ItemStack cursorStack = menu.getCarried();
                if (!cursorStack.isEmpty()) {
                    ItemStack remaining = WarehouseManager.addFluid(warehouse, cursorStack, player);
                    menu.setCarried(remaining);
                } else {
                    int amount = (button == 1) ? 1 : 64;
                    ItemStack taken = WarehouseManager.removeItem(warehouse, slotId - warehouseStart, amount, false);
                    menu.setCarried(taken);
                }
                ci.cancel();
            }
        } 
        // 处理升级槽位
        else if (slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            PlayerWarehouse warehouse = com.portablestorage.component.ModComponents.get(player).getWarehouse(player.getUUID());
            if (!warehouse.isEnabled()) return;

            if (!player.getAbilities().instabuild) {
                if (clickType == ClickType.QUICK_MOVE) {
                    ItemStack stackInSlot = slot.getItem();
                    if (!stackInSlot.isEmpty()) {
                        if (((AbstractContainerMenuAccessor)menu).invokeMoveItemStackTo(stackInSlot, 9, 45, true)) {
                            slot.set(stackInSlot);
                        }
                    }
                    ci.cancel();
                    return;
                }

                ItemStack cursorStack = menu.getCarried();
                if (!cursorStack.isEmpty()) {
                    if (slot.mayPlace(cursorStack)) {
                        ItemStack one = cursorStack.copyWithCount(1);
                        ItemStack old = slot.getItem();
                        
                        slot.set(one);
                        cursorStack.shrink(1);
                        
                        if (!old.isEmpty()) {
                            if (cursorStack.isEmpty()) {
                                menu.setCarried(old);
                            } else {
                                if (!player.getInventory().add(old)) {
                                    player.drop(old, false);
                                }
                            }
                        }
                    }
                } else {
                    ItemStack taken = slot.remove(1);
                    menu.setCarried(taken);
                }
                ci.cancel();
            }
        }
    }
}
