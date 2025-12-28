package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;
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

@Mixin(value = InventoryMenu.class, priority = 1500)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    @Shadow @Final private Player owner;

    protected InventoryMenuMixin(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addWarehouseSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        PlayerWarehouse warehouse = ModComponents.get(owner).getWarehouse(owner.getUUID());

        int startX = WarehouseConstants.SLOT_LOGIC_X; // 8
        int startY = WarehouseConstants.SLOT_LOGIC_Y_BASE; // 191
        
        // 始终添加最大数量的槽位，但根据 visibleRows 和折叠状态控制激活状态
        for (int row = 0; row < WarehouseConstants.MAX_ROWS; row++) {
            final int currentRow = row;
            for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                this.addSlot(new Slot(warehouse, col + row * WarehouseConstants.SLOTS_PER_ROW, startX + col * WarehouseConstants.SLOT_SIZE, startY + row * WarehouseConstants.SLOT_SIZE) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return true; }

                    @Override
                    public boolean isActive() {
                        // 1. 非创造模式 2. 仓库未折叠 3. 仓库已启用 4. 在当前可见行范围内
                        return !owner.getAbilities().instabuild && !warehouse.isFolded() && warehouse.isEnabled() && currentRow < warehouse.getVisibleRows();
                    }
                });
            }
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (player.getAbilities().instabuild) return;

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        
        // 如果仓库被禁用，只允许玩家将物品存入仓库（如果需要），但不允许从仓库取出
        // 这里我们统一下，禁用时直接不响应快速移动
        if (!warehouse.isEnabled()) return;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return;

        ItemStack stackInSlot = slot.getItem();
        
        // 动态定位仓库槽位起始索引
        int warehouseStart = -1;
        for (int i = 0; i < this.slots.size(); i++) {
            if (this.slots.get(i).container == warehouse) {
                warehouseStart = i;
                break;
            }
        }
        
        if (warehouseStart == -1) return;
        
        // 仓库折叠时禁止快速移动操作
        if (warehouse.isFolded()) return;

        int warehouseSlotEnd = warehouseStart + (warehouse.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW);

        if (index >= warehouseStart && index < warehouseSlotEnd) { // 从仓库快速转移到背包
            if (warehouse.isQuickInteraction()) {
            long realCount = warehouse.getRealCount(index - warehouseStart);
            int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
            ItemStack resultStack = stackInSlot.copyWithCount(toTake);
            
                // 尝试转移到背包（36-44 是快捷栏，9-35 是主背包）
                // 先尝试快捷栏
                if (!this.moveItemStackTo(resultStack, 36, 45, false)) {
                    // 失败则尝试主背包
                    if (!this.moveItemStackTo(resultStack, 9, 36, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
                }
                
                int movedCount = toTake - resultStack.getCount();
                if (movedCount > 0) {
                    warehouse.removeItem(index - warehouseStart, movedCount);
                    // 返回原本槽位中的物品副本，这会引导系统的循环逻辑继续处理，
                    // 从而实现“一组一组连续取出”的效果。
                    cir.setReturnValue(stackInSlot.copy());
                } else {
            cir.setReturnValue(ItemStack.EMPTY);
                }
            } else {
                // 如果快捷交互未开启，按住 Shift 点击仓库物品时，我们也应该拦截它，
                // 防止它流向原版逻辑导致物品被莫名其妙拿起或消失。
                cir.setReturnValue(ItemStack.EMPTY);
            }
            } else if (index >= WarehouseConstants.PLAYER_INVENTORY_START && index < WarehouseConstants.PLAYER_INVENTORY_END) { // 从玩家背包快速转移到仓库
            if (warehouse.isQuickInteraction()) {
                    warehouse.addItem(stackInSlot);
                    // 如果 stackInSlot 还没变空，说明有溢出部分留在原槽位，不需要 set(EMPTY)
                    if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
                    }
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }
}
