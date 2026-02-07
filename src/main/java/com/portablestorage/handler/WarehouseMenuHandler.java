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

/**
 * 仓库菜单处理器
 * 负责向容器菜单注入仓库槽位和升级槽位，处理快捷移动逻辑
 */
public class WarehouseMenuHandler {

    /**
     * 向任意菜单注入仓库槽位和升级槽位
     */
    public static void injectWarehouseSlots(AbstractContainerMenu menu, Player player) {
        if (player == null)
            return;

        // 排除创造模式所有相关界面（包括 ItemPickerMenu）
        if (player.getAbilities().instabuild) {
            String menuName = menu.getClass().getName();
            if (menu instanceof InventoryMenu || menuName.contains("Creative") || menuName.contains("ItemPicker")) {
                return;
            }
        }

        // 仅在适配过的界面注入
        if (!isAdaptedMenu(menu)) {
            return;
        }

        // 容器界面需要工作台升级才能注入
        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (isContainerMenu(menu) && warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
            return;
        }

        // 防止重复注入
        for (Slot slot : menu.slots) {
            if (slot.container instanceof PlayerWarehouse)
                return;
        }

        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;

        int startX = -1000;
        int startY = -1000;

        // 添加升级槽位
        for (int i = 0; i < WarehouseConstants.MAX_ROWS; i++) {
            accessor.invokeAddSlot(new UpgradeSlot(warehouse, player, i, startX, startY) {
                @Override
                public boolean isActive() {
                    if (player.getAbilities().instabuild) {
                        String menuName = menu.getClass().getName();
                        if (menu instanceof InventoryMenu || menuName.contains("Creative")
                                || menuName.contains("ItemPicker")) {
                            return false;
                        }
                    }
                    // 在容器界面，必须持有工作台升级才激活
                    if (isContainerMenu(menu)
                            && warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
                        return false;
                    }
                    return super.isActive();
                }
            });
        }

        // 添加仓库槽位
        for (int row = 0; row < WarehouseConstants.MAX_ROWS; row++) {
            final int currentRow = row;
            for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                accessor.invokeAddSlot(
                        new Slot(warehouse, col + row * WarehouseConstants.SLOTS_PER_ROW, startX, startY) {
                            @Override
                            public boolean mayPlace(ItemStack stack) {
                                return true;
                            }

                            @Override
                            public boolean isActive() {
                                if (player.getAbilities().instabuild) {
                                    String menuName = menu.getClass().getName();
                                    if (menu instanceof InventoryMenu || menuName.contains("Creative")
                                            || menuName.contains("ItemPicker")) {
                                        return false;
                                    }
                                }
                                // 在容器界面，必须持有工作台升级才激活
                                if (isContainerMenu(menu) && warehouse
                                        .getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
                                    return false;
                                }
                                return !warehouse.isFolded() && warehouse.isEnabled()
                                        && currentRow < warehouse.getVisibleRows();
                            }
                        });
            }
        }
    }

    /**
     * 为 InventoryMenu 注入 3x3 合成槽位
     */
    public static void injectCraftingSlots(AbstractContainerMenu menu, CraftingContainer craftSlots, Player owner) {
        if (!(menu instanceof InventoryMenu))
            return;

        int[] extraIndices = { 2, 5, 6, 7, 8 };
        int[][] positions = {
                { WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y },
                { WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 18 },
                { WarehouseConstants.CRAFT_3X3_X, WarehouseConstants.CRAFT_3X3_Y + 2 * 18 },
                { WarehouseConstants.CRAFT_3X3_X + 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18 },
                { WarehouseConstants.CRAFT_3X3_X + 2 * 18, WarehouseConstants.CRAFT_3X3_Y + 2 * 18 }
        };

        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
        for (int i = 0; i < extraIndices.length; i++) {
            final int idx = extraIndices[i];
            accessor.invokeAddSlot(new Slot(craftSlots, idx, positions[i][0], positions[i][1]) {
                @Override
                public boolean isActive() {
                    return WarehouseUtils.is3x3Enabled(owner);
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return WarehouseUtils.is3x3Enabled(owner);
                }
            });
        }
    }

    public static ItemStack handleQuickMove(AbstractContainerMenu menu, Player player, int index) {
        // 仅处理已适配的界面，避免在精妙背包等模组菜单上误用 slotId 导致越界
        if (!isAdaptedMenu(menu))
            return null;
        if (index < 0 || index >= menu.slots.size())
            return null;

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (!warehouse.isEnabled())
            return null;

        Slot slot = menu.slots.get(index);
        boolean isWarehouseSlot = slot.container instanceof PlayerWarehouse || slot instanceof UpgradeSlot;

        // 合成结果槽位完全交给原版逻辑处理
        if (slot instanceof ResultSlot) {
            return null;
        }

        // 在容器界面，如果没有工作台升级，禁止快捷移动到仓库
        if (isContainerMenu(menu) && warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
            if (isWarehouseSlot)
                return ItemStack.EMPTY;
            return null;
        }
        if (slot == null || !slot.hasItem())
            return null;

        ItemStack stackInSlot = slot.getItem();
        ItemStack originalStack = stackInSlot.copy(); // 保留副本用于返回

        // 处理仓库槽位和升级槽位
        if (slot.container instanceof PlayerWarehouse warehouseSlot) {
            // 未适配界面不应响应仓库交互
            if (!isAdaptedMenu(menu)) {
                return null;
            }
            // 快速交互：从仓库取出物品到背包
            if (warehouse.isQuickInteraction() && !warehouse.isFolded()) {
                int containerSlot = slot.getContainerSlot();
                com.portablestorage.logic.WarehouseManager.tryTransferToInventory(warehouseSlot, containerSlot, player);
                menu.broadcastChanges();
                return ItemStack.EMPTY; // 返回空表示已处理
            }
            // 如果没有快速交互，返回null让原版逻辑处理（或者返回EMPTY阻止交互）
            return ItemStack.EMPTY; // 阻止原版快速移动逻辑
        }
        if (slot instanceof UpgradeSlot) {
            // 未适配界面不应响应升级槽位交互
            if (!isAdaptedMenu(menu)) {
                return null;
            }
            if (!((AbstractContainerMenuAccessor) menu).invokeMoveItemStackTo(stackInSlot, 9, 45, true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return originalStack; // 返回副本表示成功
        }

        // 未适配界面不应响应快速存取
        if (!isAdaptedMenu(menu)) {
            return null;
        }

        // 快速交互：尝试存入仓库
        if (warehouse.isQuickInteraction() && !warehouse.isFolded()) {
            // 优先尝试作为流体桶存入
            ItemStack remaining = WarehouseManager.addFluid(warehouse, stackInSlot, player);

            // 非流体则作为普通物品存入
            if (remaining.getCount() == originalStack.getCount()) {
                WarehouseManager.addItem(warehouse, stackInSlot, player);
                remaining = stackInSlot;
            }

            if (remaining.getCount() < originalStack.getCount()) {
                slot.set(remaining);
                slot.setChanged();
                return originalStack; // 返回副本表示成功
            }
        }

        // 未开启快速交互或仓库已满：在容器和背包间移动
        int invStart = -1;
        int invEnd = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
                if (invStart == -1)
                    invStart = i;
                invEnd = i + 1;
            }
        }

        if (invStart != -1) {
            AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
            boolean moved = false;
            // 从背包移入容器
            if (index >= invStart && index < invEnd) {
                moved = accessor.invokeMoveItemStackTo(stackInSlot, 0, invStart, false);
            }
            // 从容器移入背包
            else if (index < invStart) {
                moved = accessor.invokeMoveItemStackTo(stackInSlot, invStart, invEnd, true);
            }

            if (moved) {
                if (stackInSlot.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                return originalStack; // 返回原始副本表示成功
            }
        }

        return null; // 返回 null 让原版逻辑继续处理
    }

    public static ItemStack handleCraftingQuickMove(AbstractContainerMenu menu, List<Slot> slots,
            CraftingContainer craftSlots, Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem())
            return null;

        if (WarehouseUtils.is3x3Enabled(player)) {
            if (slot instanceof ResultSlot || slot.container == craftSlots) {
                ItemStack stackInSlot = slot.getItem();
                ItemStack resultStack = stackInSlot.copy();

                int invStart = -1;
                int invEnd = -1;
                for (int i = 0; i < slots.size(); i++) {
                    Slot s = slots.get(i);
                    if (s.container instanceof Inventory && s.getContainerSlot() < 36) {
                        if (invStart == -1)
                            invStart = i;
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

    public static boolean isContainerMenu(AbstractContainerMenu menu) {
        String name = menu.getClass().getName();
        return !(menu instanceof InventoryMenu)
                && !name.contains("CraftingWarehouseScreenHandler")
                && !name.contains("BoundBarrelScreenHandler");
    }

    /**
     * 检查菜单是否为已适配的界面
     * 只有适配了背景渲染的界面才允许注入槽位
     */
    public static boolean isAdaptedMenu(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu)
            return true;

        String name = menu.getClass().getName();
        if (name.contains("CraftingWarehouseScreenHandler") || name.contains("BoundBarrelScreenHandler")) {
            return true;
        }

        return menu instanceof ChestMenu
                || menu instanceof HopperMenu
                || menu instanceof ShulkerBoxMenu
                || menu instanceof DispenserMenu
                || menu instanceof BrewingStandMenu
                || menu instanceof BeaconMenu
                || menu instanceof EnchantmentMenu
                || menu instanceof LoomMenu
                || menu instanceof CartographyTableMenu
                || menu instanceof StonecutterMenu
                || menu instanceof FurnaceMenu
                || menu instanceof BlastFurnaceMenu
                || menu instanceof SmokerMenu
                || menu instanceof AnvilMenu
                || menu instanceof GrindstoneMenu
                || menu instanceof SmithingMenu;
    }
}
