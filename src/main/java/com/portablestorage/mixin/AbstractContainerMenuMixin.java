package com.portablestorage.mixin;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
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
        if (slot.container instanceof PlayerWarehouse warehouse) {
            // 如果仓库被禁用，不响应任何仓库点击逻辑
            if (!warehouse.isEnabled()) return;

            // 查找起始索引以计算相对索引
            int warehouseStart = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container == warehouse) {
                    warehouseStart = i;
                    break;
                }
            }
            if (warehouseStart == -1) return;

            // 只有被激活的仓库槽位才响应点击
            if (!player.getAbilities().instabuild) {
                // Shift+点击现在由客户端通过网络包处理，这里只需要拦截防止原版逻辑
                if (clickType == ClickType.QUICK_MOVE) {
                    ci.cancel();
                    return;
                }

                        ItemStack cursorStack = menu.getCarried();
                        if (!cursorStack.isEmpty()) {
                            // 存入
                            ItemStack remaining = WarehouseManager.addFluid(warehouse, cursorStack, player);
                            menu.setCarried(remaining);
                        } else {
                    // 取出
                    int amount = (button == 1) ? 1 : 64;
                    ItemStack taken = WarehouseManager.removeItem(warehouse, slotId - warehouseStart, amount, false);
                    menu.setCarried(taken);
                }
                ci.cancel();
            }
        }
    }
}

