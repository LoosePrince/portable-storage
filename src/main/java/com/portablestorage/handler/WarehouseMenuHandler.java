package com.portablestorage.handler;

import java.util.List;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.upgrade.UpgradeSlot;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseUtils;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;

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
        // 1. 基础检查
        if (!isAdaptedMenu(menu))
            return null;
        if (index < 0 || index >= menu.slots.size())
            return null;

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (!warehouse.isEnabled())
            return null;

        Slot slot = menu.slots.get(index);
        if (slot == null || !slot.hasItem())
            return null;

        // 2. 识别槽位类型
        boolean isWarehouseSlot = slot.container instanceof PlayerWarehouse;
        boolean isUpgradeSlot = slot instanceof UpgradeSlot;
        boolean isPlayerInventory = slot.container instanceof Inventory;

        // 3. 特殊限制检查
        // 在容器界面，如果没有工作台升级，禁止快捷移动到/从仓库
        if (isContainerMenu(menu) && warehouse.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
            if (isWarehouseSlot || isUpgradeSlot)
                return ItemStack.EMPTY;
            return null;
        }

        ItemStack stackInSlot = slot.getItem();
        ItemStack originalStack = stackInSlot.copy();

        // 4. 分支处理

        // 分支 A: 仓库槽位（取出到背包）
        if (isWarehouseSlot) {
            if (!warehouse.isFolded()) {
                int containerSlot = slot.getContainerSlot();
                com.portablestorage.logic.WarehouseManager.tryTransferToInventory((PlayerWarehouse) slot.container,
                        containerSlot, player);
                menu.broadcastChanges();
            }
            return ItemStack.EMPTY;
        }

        // 分支 B: 升级槽位（取出到背包）
        if (isUpgradeSlot) {
            if (!((AbstractContainerMenuAccessor) menu).invokeMoveItemStackTo(stackInSlot, 9, 45, true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return originalStack;
        }

        // 分支 C: 玩家背包槽位
        if (isPlayerInventory) {
            if (warehouse.isQuickInteraction() && !warehouse.isFolded()) {
                // 尝试存入仓库
                ItemStack remaining = WarehouseManager.addFluid(warehouse, stackInSlot, player);
                if (remaining.getCount() == originalStack.getCount()) {
                    WarehouseManager.addItem(warehouse, stackInSlot, player);
                    remaining = stackInSlot;
                }

                if (remaining.getCount() < originalStack.getCount()) {
                    slot.set(remaining);
                    slot.setChanged();
                    return originalStack;
                }
            }
            // 存入失败或未开启快速存取，交给原版处理（移动到容器）
            return null;
        }

        // 分支 D: 普通容器槽位（如铁砧结果、熔炉、箱子等）
        if (warehouse.isQuickInteraction() && !warehouse.isFolded() && !isSpecialSlot(slot, menu)) {
            // 尝试存入仓库
            ItemStack remaining = WarehouseManager.addFluid(warehouse, stackInSlot, player);
            if (remaining.getCount() == originalStack.getCount()) {
                WarehouseManager.addItem(warehouse, stackInSlot, player);
                remaining = stackInSlot;
            }

            if (remaining.getCount() < originalStack.getCount()) {
                slot.set(remaining);
                slot.setChanged();
                return originalStack;
            }
        }

        // 始终交给原版处理，确保触发 onTake 等逻辑，并移动到玩家背包
        return null;
    }

    /**
     * 识别具有特殊逻辑的槽位（如合成结果槽），这些槽位不应直接存入仓库
     */
    private static boolean isSpecialSlot(Slot slot, AbstractContainerMenu menu) {
        // 1. 基础类型检查
        if (slot instanceof ResultSlot)
            return true;

        // 2. 类名检查（涵盖 FurnaceResultSlot, CraftingResultSlot 等）
        String className = slot.getClass().getSimpleName();
        if (className.contains("Result"))
            return true;

        // 3. 针对特定菜单的索引检查（针对没有继承 ResultSlot 的匿名内部类）
        int index = slot.getContainerSlot();
        if (menu instanceof AnvilMenu && index == 2)
            return true;
        if (menu instanceof SmithingMenu && index == 3)
            return true;
        if (menu instanceof LoomMenu && index == 3)
            return true;
        if (menu instanceof CartographyTableMenu && index == 2)
            return true;
        if (menu instanceof GrindstoneMenu && index == 2)
            return true;
        if (menu instanceof StonecutterMenu && index == 1)
            return true;
        if (menu instanceof MerchantMenu && index == 2)
            return true;

        return false;
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
                            currentResult.getItem().onCraftedBy(currentResult, player);
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
