package com.portablestorage.upgrade;

import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class UpgradeSlot extends Slot {
    private final PlayerWarehouse warehouse;
    private final Player player;
    private final int visualIndex; // 在当前列中的视觉位置 (0 到 MAX_ROWS-1)

    public UpgradeSlot(PlayerWarehouse warehouse, Player player, int visualIndex, int x, int y) {
        // 使用固定索引 visualIndex，不再覆盖 getContainerSlot()
        super(warehouse.upgradeContainer, visualIndex, x, y); 
        this.warehouse = warehouse;
        this.player = player;
        this.visualIndex = visualIndex;
    }

    public int getVisualIndex() {
        return visualIndex;
    }

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
        if (player.getAbilities().instabuild) return false;
        return visualIndex < warehouse.getVisibleRows() && getUpgradeType() != null && warehouse.isEnabled() && !warehouse.isFolded();
    }

    @Override
    public boolean mayPickup(Player player) {
        return true;
    }
}
