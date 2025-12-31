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
                        ItemStack stackInSlot = slot.getItem();
                        int maxPlace = slot.getMaxStackSize();
                        
                        if (stackInSlot.isEmpty()) {
                            // 槽位为空，放入尽可能多的物品
                            int toPlace = Math.min(cursorStack.getCount(), maxPlace);
                            slot.set(cursorStack.split(toPlace));
                        } else if (ItemStack.isSameItemSameComponents(stackInSlot, cursorStack)) {
                            // 物品相同，尝试合并
                            int canAdd = Math.min(cursorStack.getCount(), maxPlace - stackInSlot.getCount());
                            if (canAdd > 0) {
                                stackInSlot.grow(canAdd);
                                cursorStack.shrink(canAdd);
                                slot.setChanged();
                            }
                        } else if (cursorStack.getCount() == 1) {
                            // 物品不同且手持只有1个，尝试交换
                            ItemStack old = slot.getItem();
                            slot.set(cursorStack.split(1));
                            menu.setCarried(old);
                        }
                    }
                } else {
                    // 点击取出，左键取出一组，右键取出1个
                    int amount = (button == 1) ? 1 : slot.getMaxStackSize();
                    ItemStack taken = slot.remove(amount);
                    menu.setCarried(taken);
                }
                ci.cancel();
            }
        }
    }
}
