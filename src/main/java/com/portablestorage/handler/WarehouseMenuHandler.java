package com.portablestorage.handler;

import java.util.List;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.upgrade.UpgradeSlot;
import com.portablestorage.util.CompatibilityDebug;
import com.portablestorage.util.FakePlayerUtils;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.util.WarehouseUtils;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;


// =========================================================================================
// 【重要架构说明 / 提示 AI 助手 / Architecture Note for AI & Developers】
// 
// 致开发者与 AI 助手 (To Developers & AI Assistants):
// 1. 本方法必须在客户端与服务端【绝对保持 1:1 对称同步注入】menu.slots。
// 2. 请勿在此处添加 `isClientSide()` 拦截、延迟注入或基于仓库状态的条件过滤！
//    原因：Minecraft 容器网络协议依赖客户端与服务端槽位列表索引完全一致。
//    若客户端未同步注入，会导致后续添加槽位的模组（如 Trinkets 饰品栏、Traveler's Backpack 旅行背包）
//    产生约 120 个槽位的索引错位，进而引发槽位无法点击与幻影物品（Ghost items）问题。
// 3. 仓库折叠或未开启时的安全性已由 `slot.isActive() == false` 保证，原版逻辑会自动跳过这些槽位，
//    因此保留槽位结构是完全安全的。
// =========================================================================================

/**
 * Warehouse Menu Handler.
 * Injects warehouse slots and upgrade slots into container menus and handles quick-move logic dynamically.
 */
public class WarehouseMenuHandler {

    /**
     * Injects warehouse slots and upgrade slots symmetrically into any adapted container menu.
     */
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

        CompatibilityDebug.logOnce("warehouse-inject:" + menu.getClass().getName(), "menu",
                () -> "injecting warehouse slots into " + menu.getClass().getName() + "; existingSlots=" + menu.slots.size());

        // Prevent double injection.
        for (Slot slot : menu.slots) {
            if (slot.container instanceof PlayerWarehouse)
                return;
        }

        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;

        int startX = -1000;
        int startY = -1000;

        // Add upgrade slots.
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

        // Add main warehouse slots.
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

    /**
     * Injects extra 3x3 crafting slots into the InventoryMenu.
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
        CompatibilityDebug.log("crafting", () -> "adding five 3x3 inventory crafting slots for "
                + (owner == null ? "none" : owner.getClass().getName()));
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

    /**
     * Dynamically locates the start and end index in menu.slots corresponding to the player's inventory.
     * Fully compatible with Trinkets, Backpacks, and other slot-modifying mods.
     */
    public static int[] findPlayerInventoryRange(List<Slot> slots) {
        int start = -1;
        int end = -1;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory && !(slot instanceof UpgradeSlot)) {
                int containerSlot = slot.getContainerSlot();
                if (containerSlot >= 0 && containerSlot < 36) {
                    if (start == -1) {
                        start = i;
                    }
                    end = i + 1;
                }
            }
        }
        int[] range = start == -1 ? null : new int[] { start, end };
        CompatibilityDebug.log("inventory-range", () -> {
            if (range == null) {
                return "no player inventory slots found; menuSlotCount=" + slots.size();
            }
            int expected = 0;
            int gaps = 0;
            for (int i = range[0]; i < range[1]; i++) {
                Slot slot = slots.get(i);
                boolean isPlayerSlot = slot.container instanceof Inventory
                        && !(slot instanceof UpgradeSlot)
                        && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36;
                if (!isPlayerSlot) {
                    gaps++;
                } else {
                    expected++;
                }
            }
            return "range=[" + range[0] + "," + range[1] + ") matched=" + expected + "/"
                    + (range[1] - range[0]) + " interleavedNonPlayerSlots=" + gaps;
        });
        return range;
    }

    public static boolean moveUpgradeToPlayerInventory(AbstractContainerMenu menu, Slot upgradeSlot) {
        if (!(upgradeSlot instanceof UpgradeSlot) || !upgradeSlot.hasItem()) {
            return false;
        }

        int[] inventoryRange = findPlayerInventoryRange(menu.slots);
        if (inventoryRange == null) {
            return false;
        }

        ItemStack stackInSlot = upgradeSlot.getItem();
        AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
        if (!accessor.invokeMoveItemStackTo(stackInSlot, inventoryRange[0], inventoryRange[1], true)) {
            return false;
        }

        upgradeSlot.set(stackInSlot);
        upgradeSlot.setChanged();
        return true;
    }

    public static boolean handleUpgradeSlotClick(AbstractContainerMenu menu, Slot upgradeSlot, int button) {
        if (!(upgradeSlot instanceof UpgradeSlot) || (button != 0 && button != 1)) {
            return false;
        }

        ItemStack cursorStack = menu.getCarried();
        if (cursorStack.isEmpty()) {
            menu.setCarried(upgradeSlot.remove(upgradeRemovalAmount(button, upgradeSlot.getMaxStackSize())));
            return true;
        }

        if (!upgradeSlot.mayPlace(cursorStack)) {
            return true;
        }

        ItemStack stackInSlot = upgradeSlot.getItem();
        int maxPlace = upgradeSlot.getMaxStackSize();
        if (stackInSlot.isEmpty()) {
            int toPlace = Math.min(cursorStack.getCount(), maxPlace);
            upgradeSlot.set(cursorStack.split(toPlace));
        } else if (ItemStack.isSameItemSameComponents(stackInSlot, cursorStack)) {
            int canAdd = Math.min(cursorStack.getCount(), maxPlace - stackInSlot.getCount());
            if (canAdd > 0) {
                stackInSlot.grow(canAdd);
                cursorStack.shrink(canAdd);
                upgradeSlot.setChanged();
            }
        } else if (cursorStack.getCount() == 1) {
            ItemStack previous = stackInSlot.copy();
            upgradeSlot.set(cursorStack.split(1));
            menu.setCarried(previous);
        }
        return true;
    }

    public static int upgradeRemovalAmount(int button, int maxStackSize) {
        return button == 1 ? 1 : maxStackSize;
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
        if (!slot.hasItem())
            return null;

        boolean isWarehouseSlot = slot.container instanceof PlayerWarehouse;
        boolean isUpgradeSlot = slot instanceof UpgradeSlot;
        boolean isPlayerInventory = slot.container instanceof Inventory;

        ItemStack stackInSlot = slot.getItem();
        ItemStack originalStack = stackInSlot.copy();

        int[] inventoryRange = findPlayerInventoryRange(menu.slots);

        // Branch A: Warehouse slot -> Move to Player Inventory
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

        // Branch B: Upgrade slot -> Move to Player Inventory
        if (isUpgradeSlot) {
            return moveUpgradeToPlayerInventory(menu, slot) ? originalStack : ItemStack.EMPTY;
        }

        // Branch C: Player Inventory slot -> Store into Warehouse
        if (isPlayerInventory) {
            if (storeSlotIntoWarehouse(player, warehouse, slot, stackInSlot, originalStack,
                    "menu_quick_move.player_to_warehouse")) {
                return originalStack;
            }
            return null;
        }

        // Pass-through for custom mod slots in InventoryMenu (e.g., Traveler's Backpack or Trinkets slots)
        if (menu instanceof InventoryMenu) {
            return null;
        }

        // Branch D: Standard Container slot -> Store into Warehouse or fallback to Player Inventory
        if (!isSpecialSlot(slot, menu)) {
            if (storeSlotIntoWarehouse(player, warehouse, slot, stackInSlot, originalStack,
                    "menu_quick_move.container_to_warehouse")) {
                return originalStack;
            }

            if (inventoryRange != null) {
                AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
                if (accessor.invokeMoveItemStackTo(stackInSlot, inventoryRange[0], inventoryRange[1], true)) {
                    slot.setChanged();
                    notifyCraftingChanged(menu);
                    return originalStack;
                }
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
            }

            if (stackInSlot.getCount() >= originalStack.getCount()) {
                return false;
            }

            slot.set(stackInSlot);
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
        return (menu instanceof AnvilMenu && index == 2)
                || (menu instanceof SmithingMenu && index == 3)
                || (menu instanceof LoomMenu && index == 3)
                || (menu instanceof CartographyTableMenu && index == 2)
                || (menu instanceof GrindstoneMenu && index == 2)
                || (menu instanceof StonecutterMenu && index == 1)
                || (menu instanceof MerchantMenu && index == 2);
    }

    public static ItemStack handleCraftingQuickMove(AbstractContainerMenu menu, List<Slot> slots,
                                                    CraftingContainer craftSlots, Player player, int index) {
        if (player == null || FakePlayerUtils.isFakePlayer(player))
            return null;

        Slot slot = slots.get(index);
        if (!slot.hasItem())
            return null;

        if (WarehouseUtils.is3x3Enabled(player)) {
            if (slot instanceof ResultSlot || slot.container == craftSlots) {
                ItemStack stackInSlot = slot.getItem();
                ItemStack resultStack = stackInSlot.copy();

                int[] invRange = findPlayerInventoryRange(slots);
                if (invRange == null) return ItemStack.EMPTY;

                AbstractContainerMenuAccessor accessor = (AbstractContainerMenuAccessor) menu;
                if (slot instanceof ResultSlot) {
                    while (slot.hasItem()) {
                        ItemStack currentResult = slot.getItem();
                        ItemStack resultCopy = currentResult.copy();
                        currentResult.getItem().onCraftedBy(currentResult, player);
                        if (!accessor.invokeMoveItemStackTo(currentResult, invRange[0], invRange[1], true)) {
                            break;
                        }
                        slot.onQuickCraft(currentResult, resultCopy);
                        slot.onTake(player, currentResult);
                        if (currentResult.getCount() == resultCopy.getCount()) {
                            break;
                        }
                    }
                } else {
                    if (!accessor.invokeMoveItemStackTo(stackInSlot, invRange[0], invRange[1], false)) {
                        return ItemStack.EMPTY;
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