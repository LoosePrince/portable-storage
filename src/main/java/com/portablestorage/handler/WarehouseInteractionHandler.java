package com.portablestorage.handler;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.upgrade.ExperienceUpgrade;
import com.portablestorage.util.WarehouseUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WarehouseInteractionHandler {

    public static boolean handleClicked(AbstractContainerMenu menu, int slotId, int button, ClickType clickType,
            Player player) {
        // 排除创造模式背包菜单的交互
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu && player.getAbilities().instabuild)
            return false;

        if (slotId < 0 || slotId >= menu.slots.size())
            return false;

        Slot slot = menu.slots.get(slotId);
        boolean isWarehouseSlot = slot.container instanceof PlayerWarehouse
                || slot instanceof com.portablestorage.upgrade.UpgradeSlot;

        // 在容器界面，如果没有工作台升级，禁止交互仓库相关槽位
        PlayerWarehouse warehouseCheck = ModComponents.get(player).getWarehouse(player.getUUID());
        if (WarehouseMenuHandler.isContainerMenu(menu)
                && warehouseCheck.getUpgrade(com.portablestorage.upgrade.WorkbenchUpgrade.ID).isEmpty()) {
            if (isWarehouseSlot)
                return true; // 拦截并取消
            return false;
        }

        // Handle main warehouse slots
        if (slot.container instanceof PlayerWarehouse warehouse) {
            if (!warehouse.isEnabled())
                return false;
            int warehouseStart = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container == warehouse) {
                    warehouseStart = i;
                    break;
                }
            }
            if (warehouseStart == -1)
                return false;

            if (clickType == ClickType.QUICK_MOVE) {
                return true; // Cancelled
            }

            // Handle experience upgrade interaction
            if (slot.hasItem() && slot.getItem().is(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE)) {
                handleExperienceClick(warehouse, slotId - warehouseStart, button, clickType, player);
                return true; // Cancelled
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
            return true; // Cancelled
        }
        // Handle upgrade slots
        else if (slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
            if (!warehouse.isEnabled())
                return false;

            if (clickType == ClickType.QUICK_MOVE) {
                ItemStack stackInSlot = slot.getItem();
                if (!stackInSlot.isEmpty()) {
                    if (((AbstractContainerMenuAccessor) menu).invokeMoveItemStackTo(stackInSlot, 9, 45, true)) {
                        slot.set(stackInSlot);
                    }
                }
                return true; // Cancelled
            }

            ItemStack cursorStack = menu.getCarried();
            if (!cursorStack.isEmpty()) {
                if (slot.mayPlace(cursorStack)) {
                    ItemStack stackInSlot = slot.getItem();
                    int maxPlace = slot.getMaxStackSize();

                    if (stackInSlot.isEmpty()) {
                        int toPlace = Math.min(cursorStack.getCount(), maxPlace);
                        slot.set(cursorStack.split(toPlace));
                    } else if (ItemStack.isSameItemSameComponents(stackInSlot, cursorStack)) {
                        int canAdd = Math.min(cursorStack.getCount(), maxPlace - stackInSlot.getCount());
                        if (canAdd > 0) {
                            stackInSlot.grow(canAdd);
                            cursorStack.shrink(canAdd);
                            slot.setChanged();
                        }
                    } else if (cursorStack.getCount() == 1) {
                        ItemStack old = slot.getItem();
                        slot.set(cursorStack.split(1));
                        menu.setCarried(old);
                    }
                }
            } else {
                int amount = (button == 1) ? 1 : slot.getMaxStackSize();
                ItemStack taken = slot.remove(amount);
                menu.setCarried(taken);
            }
            return true; // Cancelled
        }
        return false;
    }

    private static void handleExperienceClick(PlayerWarehouse warehouse, int slotIndex, int button, ClickType clickType,
            Player player) {
        if (player.level().isClientSide)
            return;

        ItemStack upgradeStack = warehouse.getUpgrade(ExperienceUpgrade.ID);
        if (upgradeStack.isEmpty())
            return;

        int levels = ExperienceUpgrade.getStep(upgradeStack);
        ItemStack cursorStack = player.containerMenu.getCarried();

        if (cursorStack.isEmpty()) {
            if (button == 1) { // Right click: Deposit
                int levelsToMove = levels;
                long totalToStore = 0;
                for (int i = 0; i < levelsToMove; i++) {
                    int lvl = Math.max(0, player.experienceLevel - i);
                    totalToStore += (ExperienceUpgrade.getExperienceForLevel(lvl)
                            - ExperienceUpgrade.getExperienceForLevel(Math.max(0, lvl - 1)));
                }

                long currentProgressXp = Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
                totalToStore += currentProgressXp;

                if (totalToStore > 0) {
                    warehouse.addExperience(totalToStore);
                    ExperienceUpgrade.addExperience(player, (int) -totalToStore);
                }
            } else if (button == 0) { // Left click: Withdraw
                long totalToTake = 0;
                for (int i = 0; i < levels; i++) {
                    int lvl = player.experienceLevel + i;
                    totalToTake += (ExperienceUpgrade.getExperienceForLevel(lvl + 1)
                            - ExperienceUpgrade.getExperienceForLevel(lvl));
                }

                long canTake = Math.min(totalToTake, warehouse.getExperience());
                if (canTake > 0) {
                    warehouse.addExperience(-canTake);
                    ExperienceUpgrade.addExperience(player, (int) canTake);
                }
            }
        } else if (cursorStack.is(Items.GLASS_BOTTLE) && button == 1) {
            int canConvert = (int) (warehouse.getExperience() / 11);
            int toConvert = Math.min(cursorStack.getCount(), canConvert);

            if (toConvert > 0) {
                warehouse.addExperience(-(long) toConvert * 11);
                ItemStack bottles = new ItemStack(Items.EXPERIENCE_BOTTLE, toConvert);
                cursorStack.shrink(toConvert);
                if (!player.getInventory().add(bottles)) {
                    player.drop(bottles, false);
                }
            }

            if (!cursorStack.isEmpty() && canConvert < cursorStack.getCount()) {
                WarehouseManager.addItem(warehouse, cursorStack);
            }
        }
    }
}
