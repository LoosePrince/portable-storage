package com.portablestorage.handler;

import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.FakePlayerUtils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public class WarehouseInteractionHandler {

    public static boolean handleClicked(AbstractContainerMenu menu, int slotId, int button, Object clickType,
            Player player) {
        
        if (player == null || FakePlayerUtils.isFakePlayer(player)) {
            return false;
        }

        if (menu instanceof net.minecraft.world.inventory.InventoryMenu && player.getAbilities().instabuild) {
            return false;
        }

        if (slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }

        Slot slot = menu.slots.get(slotId);
        
        if (slot.container instanceof PlayerWarehouse || slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            return slot.isActive();
        }
        
        return false;
    }
}