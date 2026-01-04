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

@Mixin(value = InventoryMenu.class, priority = 2000)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    @Shadow @Final private Player owner;

    protected InventoryMenuMixin(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addWarehouseSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        PlayerWarehouse warehouse = ModComponents.get(owner).getWarehouse(owner.getUUID());

        int startX = WarehouseConstants.getSlotLogicX();
        int startY = WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows());
        
        // 1. 添加升级槽位 (最大 MAX_ROWS 列)
        int upgradeX = WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X;
        int upgradeYBase = WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows()) + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y;
        for (int i = 0; i < WarehouseConstants.MAX_ROWS; i++) {
            this.addSlot(new com.portablestorage.upgrade.UpgradeSlot(warehouse, owner, i, upgradeX, upgradeYBase + i * WarehouseConstants.SLOT_SIZE));
        }

        // 2. 始终添加最大数量的仓库槽位
        for (int row = 0; row < WarehouseConstants.MAX_ROWS; row++) {
            final int currentRow = row;
            for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                this.addSlot(new Slot(warehouse, col + row * WarehouseConstants.SLOTS_PER_ROW, startX + col * WarehouseConstants.SLOT_SIZE, startY + row * WarehouseConstants.SLOT_SIZE) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return true; }

                    @Override
                    public boolean isActive() {
                        // 核心修复：1. 非创造模式 2. 仓库未折叠 3. 仓库已启用 4. 在当前可见行范围内
                        // 注意：创造模式下 owner.getAbilities().instabuild 为 true，此时该槽位应被禁用，防止拦截点击和悬停
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
        } else if (slot.container == warehouse.upgradeContainer) { // 从升级槽位取出
            if (!this.moveItemStackTo(stackInSlot, 9, 45, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            slot.setChanged();
            cir.setReturnValue(ItemStack.EMPTY);
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
