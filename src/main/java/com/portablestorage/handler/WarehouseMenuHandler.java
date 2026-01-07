package com.portablestorage.handler;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseUtils;
import com.portablestorage.upgrade.UpgradeSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class WarehouseMenuHandler {

    /**
     * Injects storage slots and upgrade slots into any menu.
     */
    public static void injectWarehouseSlots(AbstractContainerMenu menu, Player player) {
        if (player == null) return;
        
        // 1. 彻底排除创造模式所有相关界面 (包括 ItemPickerMenu)
        if (player.getAbilities().instabuild) {
            String menuName = menu.getClass().getName();
            if (menu instanceof InventoryMenu || menuName.contains("Creative") || menuName.contains("ItemPicker")) {
                return;
            }
        }

        // Prevent duplicate injection
        for (Slot slot : menu.slots) {
            if (slot.container instanceof PlayerWarehouse) return;
        }

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;

        int startX = -1000;
        int startY = -1000;

        // 1. Add Upgrade Slots
        for (int i = 0; i < WarehouseConstants.MAX_ROWS; i++) {
            accessor.invokeAddSlot(new UpgradeSlot(warehouse, player, i, startX, startY) {
                @Override
                public boolean isActive() {
                    if (player.getAbilities().instabuild) {
                        String menuName = menu.getClass().getName();
                        if (menu instanceof InventoryMenu || menuName.contains("Creative") || menuName.contains("ItemPicker")) {
                            return false;
                        }
                    }
                    return super.isActive();
                }
            });
        }

        // 2. Add Warehouse Slots
        for (int row = 0; row < WarehouseConstants.MAX_ROWS; row++) {
            final int currentRow = row;
            for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                accessor.invokeAddSlot(new Slot(warehouse, col + row * WarehouseConstants.SLOTS_PER_ROW, startX, startY) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return true; }

                    @Override
                    public boolean isActive() {
                        if (player.getAbilities().instabuild) {
                            String menuName = menu.getClass().getName();
                            if (menu instanceof InventoryMenu || menuName.contains("Creative") || menuName.contains("ItemPicker")) {
                                return false;
                            }
                        }
                        return !warehouse.isFolded() && warehouse.isEnabled() && currentRow < warehouse.getVisibleRows();
                    }
                });
            }
        }
    }

    /**
     * Handles 3x3 crafting grid extension for InventoryMenu.
     */
    public static void injectCraftingSlots(AbstractContainerMenu menu, CraftingContainer craftSlots, Player owner) {
        if (!(menu instanceof InventoryMenu)) return;
        
        int[] extraIndices = {2, 5, 6, 7, 8};
        int[][] positions = {
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y},
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 18},
            {WarehouseConstants.CRAFT_3X3_X, WarehouseConstants.CRAFT_3X3_Y + 2 * 18},
            {WarehouseConstants.CRAFT_3X3_X + 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18},
            {WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18}
        };

        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
        for (int i = 0; i < extraIndices.length; i++) {
            final int idx = extraIndices[i];
            accessor.invokeAddSlot(new Slot(craftSlots, idx, positions[i][0], positions[i][1]) {
                @Override
                public boolean isActive() { return WarehouseUtils.is3x3Enabled(owner); }
                @Override
                public boolean mayPlace(ItemStack stack) { return WarehouseUtils.is3x3Enabled(owner); }
            });
        }
    }

    public static ItemStack handleQuickMove(AbstractContainerMenu menu, Player player, int index) {
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (!warehouse.isEnabled()) return null;

        Slot slot = menu.slots.get(index);
        if (slot == null || !slot.hasItem()) return null;

        ItemStack stackInSlot = slot.getItem();
        ItemStack originalStack = stackInSlot.copy(); // 必须保留副本用于返回
        
        // 1. 如果点击的是仓库插槽（包括升级槽），逻辑不变
        if (slot.container instanceof PlayerWarehouse) {
            return ItemStack.EMPTY; 
        } 
        if (slot instanceof UpgradeSlot) {
            if (!((AbstractContainerMenuAccessor) menu).invokeMoveItemStackTo(stackInSlot, 9, 45, true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return originalStack; // 修复：返回副本表示成功
        }

        // 2. 如果点击的是非仓库插槽（箱子、背包等）
        // 只有开启了快速交互且仓库未折叠时，才尝试存入仓库
        if (warehouse.isQuickInteraction() && !warehouse.isFolded()) {
            // 尝试作为流体/容器存入
            ItemStack remaining = WarehouseManager.addFluid(warehouse, stackInSlot, player);
            
            // 如果不是流体（即 stackInSlot 没变，或者 remaining 还是原物），尝试作为普通物品存入
            if (remaining.getCount() == originalStack.getCount()) {
                WarehouseManager.addItem(warehouse, stackInSlot);
                remaining = stackInSlot;
            }

            if (remaining.getCount() < originalStack.getCount()) {
                slot.set(remaining);
                slot.setChanged();
                return originalStack; // 修复：返回副本表示成功
            }
        }

        // 3. 如果没开启快速交互，或者仓库已满/折叠
        // 查找背包范围（通常是 36 个槽位）
        int invStart = -1;
        int invEnd = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
                if (invStart == -1) invStart = i;
                invEnd = i + 1;
            }
        }

        if (invStart != -1) {
            AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
            boolean moved = false;
            // 如果点击的是背包，尝试移入容器（0 到 invStart 之前的槽位）
            if (index >= invStart && index < invEnd) {
                moved = accessor.invokeMoveItemStackTo(stackInSlot, 0, invStart, false);
            } 
            // 如果点击的是容器，尝试移入背包
            else if (index < invStart) {
                moved = accessor.invokeMoveItemStackTo(stackInSlot, invStart, invEnd, true);
            }
            
            if (moved) {
                if (stackInSlot.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                return originalStack; // 修复：成功移动必须返回原始副本
            }
        }

        return null; // 返回 null 让原版继续尝试执行（如果没有被我们处理）
    }

    public static ItemStack handleCraftingQuickMove(AbstractContainerMenu menu, List<Slot> slots, CraftingContainer craftSlots, Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return null;

        if (WarehouseUtils.is3x3Enabled(player)) {
            if (slot instanceof ResultSlot || slot.container == craftSlots) {
                ItemStack stackInSlot = slot.getItem();
                ItemStack resultStack = stackInSlot.copy();

                int invStart = -1;
                int invEnd = -1;
                for (int i = 0; i < slots.size(); i++) {
                    Slot s = slots.get(i);
                    if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
                        if (invStart == -1) invStart = i;
                        invEnd = i + 1;
                    }
                }

                AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
                if (slot instanceof ResultSlot) {
                    if (invStart != -1) {
                        while (slot.hasItem()) {
                            ItemStack currentResult = slot.getItem();
                            ItemStack resultCopy = currentResult.copy();
                            currentResult.getItem().onCraftedBy(currentResult, player.level(), player);
                            if (!accessor.invokeMoveItemStackTo(currentResult, invStart, invEnd, true)) {
                                break;
                            }
                            slot.onQuickCraft(currentResult, resultCopy);
                            slot.onTake(player, currentResult);
                            if (currentResult.getCount() == resultCopy.getCount()) {
                                break;
                            }
                        }
                    }
                } else {
                    if (invStart != -1) {
                        if (!accessor.invokeMoveItemStackTo(stackInSlot, invStart, invEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                    slot.onQuickCraft(stackInSlot, resultStack);
                    slot.setChanged();
                    menu.slotsChanged(craftSlots);
                }
                return ItemStack.EMPTY;
            }
        }
        return null;
    }
}
