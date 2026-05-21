package com.portablestorage.screen;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.upgrade.ToolUpgrade;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ToolWarehouseScreenHandler extends AbstractContainerMenu {
    private final PlayerWarehouse warehouse;
    private final Player player;
    private final Container toolContainer;

    public ToolWarehouseScreenHandler(int syncId, Inventory playerInventory) {
        super(ModScreenHandlers.TOOL_WAREHOUSE, syncId);
        this.player = playerInventory.player;
        this.warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        this.toolContainer = new ToolContainer(warehouse);

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new ToolSlot(toolContainer, col + row * 3, 62 + col * 18, 17 + row * 18, warehouse,
                        player));
            }
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        PlayerWarehouse current = ModComponents.get(player).getWarehouse(player.getUUID());
        return current.isEnabled() && !current.getUpgrade(ToolUpgrade.ID).isEmpty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = slot.getItem();
        original = moving.copy();

        if (index < 9) {
            if (!this.moveItemStackTo(moving, 9, 45, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(moving, 0, 9, false)) {
            return ItemStack.EMPTY;
        }

        if (moving.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (moving.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, moving);
        return original;
    }

    private static final class ToolContainer implements Container {
        private final PlayerWarehouse warehouse;

        private ToolContainer(PlayerWarehouse warehouse) {
            this.warehouse = warehouse;
        }

        @Override
        public int getContainerSize() {
            return 9;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < 9; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return warehouse.getToolSlotStack(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack current = getItem(slot);
            if (current.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int removedCount = Math.min(amount, current.getCount());
            ItemStack removed = current.copyWithCount(removedCount);
            current.shrink(removedCount);
            setItem(slot, current);
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            warehouse.setToolSlotStack(slot, stack);
        }

        @Override
        public void setChanged() {
            warehouse.markDirty();
        }

        @Override
        public boolean stillValid(Player player) {
            return warehouse.isEnabled();
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < 9; i++) {
                setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static final class ToolSlot extends Slot {
        private final PlayerWarehouse warehouse;
        private final Player player;

        private ToolSlot(Container container, int slot, int x, int y, PlayerWarehouse warehouse, Player player) {
            super(container, slot, x, y);
            this.warehouse = warehouse;
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (!WarehouseManager.canStoreItem(warehouse, stack, player, "tool_warehouse.slot")) {
                return false;
            }
            if (!getItem().isEmpty()) {
                return true;
            }
            int typeLimit = warehouse.getMaxStorageTypes();
            return typeLimit < 0 || warehouse.getStoredItemTypeCount() < typeLimit;
        }
    }
}