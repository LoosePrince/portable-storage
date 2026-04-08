package com.portablestorage.upgrade;

import java.util.List;

import com.portablestorage.component.PlayerWarehouse;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 升级槽位
 * 用于在容器菜单中显示和管理仓库升级物品
 */
public class UpgradeSlot extends Slot {
    private final PlayerWarehouse warehouse;
    /** 在当前列中的视觉位置（0 到 MAX_ROWS-1） */
    private final int visualIndex;

    public UpgradeSlot(PlayerWarehouse warehouse, int visualIndex, int x, int y) {
        super(warehouse.upgradeContainer, visualIndex, x, y);
        this.warehouse = warehouse;
        this.visualIndex = visualIndex;
    }

    /**
     * 获取视觉索引
     * 
     * @return 视觉索引
     */
    public int getVisualIndex() {
        return visualIndex;
    }

    /**
     * 获取当前槽位对应的升级类型
     * 
     * @return 升级类型，如果索引无效则返回 null
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
