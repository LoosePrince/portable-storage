package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;
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
                        // 1. 非创造模式 2. 仓库未折叠 3. 在当前可见行范围内
                        return !owner.getAbilities().instabuild && !warehouse.isFolded() && currentRow < warehouse.getVisibleRows();
                    }
                });
            }
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        
        // 仓库折叠时禁止直接点击操作
        if (!warehouse.isFolded()) {
            int warehouseSlotEnd = WarehouseConstants.WAREHOUSE_SLOT_START + (warehouse.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW);

            // 只有被激活的仓库槽位才响应点击
            if (slotId >= WarehouseConstants.WAREHOUSE_SLOT_START && slotId < warehouseSlotEnd && !player.getAbilities().instabuild) {
                ItemStack cursorStack = this.getCarried();

                if (!cursorStack.isEmpty()) {
                    // 光标有物品：存入仓库
                    warehouse.addItem(cursorStack);
                    this.setCarried(ItemStack.EMPTY); 
                } else {
                    // 光标无物品：从仓库取出
                    int amount = (button == 1) ? 1 : 64; // 右键取1个，左键取一组
                    ItemStack taken = warehouse.removeItem(slotId - WarehouseConstants.WAREHOUSE_SLOT_START, amount);
                    this.setCarried(taken); 
                }
                return;
            }
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
        
        // 仓库折叠时禁止快速移动操作
        if (warehouse.isFolded()) return;

        int warehouseSlotEnd = WarehouseConstants.WAREHOUSE_SLOT_START + (warehouse.getVisibleRows() * WarehouseConstants.SLOTS_PER_ROW);

        if (index >= WarehouseConstants.WAREHOUSE_SLOT_START && index < warehouseSlotEnd) { // 从仓库快速转移到背包
            long realCount = warehouse.getRealCount(index - WarehouseConstants.WAREHOUSE_SLOT_START);
            int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
            ItemStack resultStack = stackInSlot.copyWithCount(toTake);
            
            if (!this.moveItemStackTo(resultStack, WarehouseConstants.PLAYER_INVENTORY_START, WarehouseConstants.PLAYER_INVENTORY_END, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            warehouse.removeItem(index - WarehouseConstants.WAREHOUSE_SLOT_START, toTake - resultStack.getCount());
            cir.setReturnValue(ItemStack.EMPTY);
        } else if (index >= WarehouseConstants.PLAYER_INVENTORY_START && index < WarehouseConstants.PLAYER_INVENTORY_END) { // 从玩家背包快速转移到仓库
            if (warehouse.isQuickInteraction()) {
                warehouse.addItem(stackInSlot.copy());
                slot.set(ItemStack.EMPTY);
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }
}
