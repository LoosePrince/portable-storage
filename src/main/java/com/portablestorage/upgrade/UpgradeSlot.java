package com.portablestorage.upgrade;

import java.util.List;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Upgrade Slot
 * Used in container menus to display and manage warehouse upgrades.
 */
public class UpgradeSlot extends Slot {
    private final PlayerWarehouse warehouse;
    /** Visual position in the current column (0 to MAX_ROWS - 1) */
    private final int visualIndex;

    public UpgradeSlot(PlayerWarehouse warehouse, int visualIndex, int x, int y) {
        super(warehouse.upgradeContainer, visualIndex, x, y);
        this.warehouse = warehouse;
        this.visualIndex = visualIndex;
    }

    /**
     * Gets the visual index (row) of this upgrade slot.
     */
    public int getVisualIndex() {
        return visualIndex;
    }

    /**
     * Gets the warehouse instance owning this upgrade slot.
     */
    public PlayerWarehouse getWarehouse() {
        return warehouse;
    }

    /**
     * Gets the upgrade type corresponding to this visual slot and current scroll offset.
     */
    private UpgradeType getUpgradeType() {
        List<UpgradeType> all = UpgradeRegistry.getAllUpgrades();
        int actualIndex = visualIndex + warehouse.getUpgradeScrollOffset();
        if (actualIndex >= 0 && actualIndex < all.size()) {
            return all.get(actualIndex);
        }
        return null;
    }

    @Override
    public int getMaxStackSize() {
        UpgradeType type = getUpgradeType();
        return type != null ? type.getMaxStackSize() : 1;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        UpgradeType type = getUpgradeType();
        return type != null && type.isItemValid(stack);
    }

    @Override
    public boolean isActive() {
        return visualIndex < warehouse.getVisibleRows() && getUpgradeType() != null && warehouse.isEnabled()
                && !warehouse.isFolded();
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }
}