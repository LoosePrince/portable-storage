package com.portablestorage.mixin;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
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
        
        // 仓库折叠时禁止快速移动操作
        if (warehouse.isFolded()) return;

        if (slot.container instanceof PlayerWarehouse) { // 从仓库快速转移到背包
            if (warehouse.isQuickInteraction()) {
                // Shift+点击由 QuickTransferPayload 处理
                cir.setReturnValue(ItemStack.EMPTY);
            } else {
                // 如果快捷交互未开启，按住 Shift 点击仓库物品时，我们也应该拦截它
                cir.setReturnValue(ItemStack.EMPTY);
            }
        } else if (slot.container instanceof Inventory) { // 从玩家背包快速转移到仓库
            // 排除装备栏和副手槽位
            // Inventory.items 是 0-35 (Main + Hotbar)
            int containerSlot = slot.getContainerSlot();
            if (containerSlot >= 0 && containerSlot < 36) {
                if (warehouse.isQuickInteraction()) {
                    ItemStack remaining = WarehouseManager.addFluid(warehouse, stackInSlot, player);
                    slot.set(remaining);
                    cir.setReturnValue(ItemStack.EMPTY);
                }
            }
        }
    }
}
