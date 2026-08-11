package com.portablestorage.handler;

import java.util.List;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.upgrade.UpgradeSlot;
import com.portablestorage.util.FakePlayerUtils;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseUtils;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class WarehouseMenuHandler {

    public static void injectWarehouseSlots(AbstractContainerMenu menu, Player player) {
        if (player == null || FakePlayerUtils.isFakePlayer(player))
            return;

        if (player.getAbilities().instabuild) {
            String menuName = menu.getClass().getName();
            if (menu instanceof InventoryMenu || menuName.contains("Creative") || menuName.contains("ItemPicker")) {
                return;
            }
        }

        if (!isAdaptedMenu(menu)) {
            return;
        }

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (warehouse == null)
            return;

        for (Slot slot : menu.slots) {
            if (slot.container instanceof PlayerWarehouse)
                return;
        }

        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;

        int startX = -1000;
        int startY = -1000;

        for (int i = 0; i < WarehouseConstants.MAX_ROWS; i++) {
            accessor.invokeAddSlot(new UpgradeSlot(warehouse, i, startX, startY) {
                @Override
                public boolean isActive() {
                    if (player.getAbilities().instabuild || FakePlayerUtils.isFakePlayer(player)) {
                        return false;
                    }
                    return super.isActive();
                }
            });
        }

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
                                if (player.getAbilities().instabuild || FakePlayerUtils.isFakePlayer(player)) {
                                    return false;
                                }
                                return !warehouse.isFolded() && warehouse.isEnabled()
                                        && currentRow < warehouse.getVisibleRows();
                            }
                        });
            }
        }
    }

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
        if (player == null || FakePlayerUtils.isFakePlayer(player))
            return null;
        if (!isAdaptedMenu(menu))
            return null;
        if (index < 0 || index >= menu.slots.size())
            return null;

        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (warehouse == null || !warehouse.isEnabled())
            return null;

        Slot slot = menu.slots.get(index);
        if (slot == null || !slot.hasItem())
            return null;

        boolean isWarehouseSlot = slot.container instanceof PlayerWarehouse;
        boolean isUpgradeSlot = slot instanceof UpgradeSlot;
        boolean isPlayerInventory = slot.container instanceof Inventory;

        ItemStack stackInSlot = slot.getItem();
        ItemStack originalStack = stackInSlot.copy();

        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;

        int invStart = -1;
        int invEnd = -1;
        List<Slot> slots = menu.slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.container instanceof Inventory && !(s instanceof UpgradeSlot)) {
                int containerSlot = s.getContainerSlot();
                if (containerSlot >= 0 && containerSlot < 36) {
                    if (invStart == -1)
                        invStart = i;
                    invEnd = i + 1;
                }
            }
        }

        if (isWarehouseSlot) {
            if (!warehouse.isFolded() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                int containerSlot = slot.getContainerSlot();
                WarehouseService.commitIfWarehouseChanged(serverPlayer, (PlayerWarehouse) slot.container,
                        "menu_quick_move.from_warehouse", () -> {
                            WarehouseManager.tryTransferToInventory((PlayerWarehouse) slot.container,
                                    containerSlot, player);
                            menu.broadcastChanges();
                            return null;
                        });
            }
            return ItemStack.EMPTY;
        }

        if (isUpgradeSlot) {
            if (invStart == -1 || !accessor.invokeMoveItemStackTo(stackInSlot, invStart, invEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return originalStack;
        }

        if (isPlayerInventory) {
            if (storeSlotIntoWarehouse(player, warehouse, slot, stackInSlot, originalStack,
                    "menu_quick_move.player_to_warehouse")) {
                return originalStack;
            }
            return null;
        }

        if (!isSpecialSlot(slot, menu)) {
            if (storeSlotIntoWarehouse(player, warehouse, slot, stackInSlot, originalStack,
                    "menu_quick_move.container_to_warehouse")) {
                return originalStack;
            }

            if (invStart != -1 && accessor.invokeMoveItemStackTo(stackInSlot, invStart, invEnd, true)) {
                slot.setChanged();
                notifyCraftingChanged(menu);
                return originalStack;
            }
            return ItemStack.EMPTY;
        }

        return null;
    }

    private static boolean storeSlotIntoWarehouse(Player player, PlayerWarehouse warehouse, Slot slot,
            ItemStack stackInSlot, ItemStack originalStack, String reason) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !warehouse.isQuickInteraction()
                || warehouse.isFolded()) {
            return false;
        }

        return WarehouseService.commitIfWarehouseChanged(serverPlayer, warehouse, reason, () -> {
            ItemStack remaining = WarehouseManager.addFluid(warehouse, stackInSlot, player, reason);
            if (remaining.getCount() == originalStack.getCount()) {
                WarehouseManager.addItem(warehouse, stackInSlot, player, reason + ".item");
                remaining = stackInSlot;
            }

            if (remaining.getCount() >= originalStack.getCount()) {
                return false;
            }

            slot.set(remaining);
            slot.setChanged();
            return true;
        });
    }

    private static boolean isSpecialSlot(Slot slot, AbstractContainerMenu menu) {
        if (slot instanceof ResultSlot || slot.container instanceof CraftingContainer)
            return true;

        String className = slot.getClass().getSimpleName();
        if (className.contains("Result") || className.contains("Crafting"))
            return true;

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
        if (player == null || FakePlayerUtils.isFakePlayer(player))
            return null;

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

    private static void notifyCraftingChanged(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (s.container instanceof CraftingContainer crafting) {
                menu.slotsChanged(crafting);
                break;
            }
        }
    }

    public static boolean isWarehouseRelatedSlot(Slot slot) {
        return slot.container instanceof PlayerWarehouse || slot instanceof UpgradeSlot;
    }

    public static boolean isContainerMenu(AbstractContainerMenu menu) {
        String name = menu.getClass().getName();
        return !(menu instanceof InventoryMenu)
                && !name.contains("CraftingWarehouseScreenHandler")
                && !name.contains("ToolWarehouseScreenHandler")
                && !name.contains("BoundBarrelScreenHandler");
    }

    public static boolean isAdaptedMenu(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu)
            return true;

        String name = menu.getClass().getName();
        if (name.contains("CraftingWarehouseScreenHandler") || name.contains("BoundBarrelScreenHandler")
                || name.contains("ToolWarehouseScreenHandler")) {
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
                || menu instanceof CrafterMenu
                || menu instanceof AnvilMenu
                || menu instanceof GrindstoneMenu
                || menu instanceof SmithingMenu;
    }
}