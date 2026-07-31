package com.portablestorage.handler;

import com.portablestorage.component.PlayerWarehouse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Warehouse Interaction Handler
 * Now heavily simplified because client-server desyncs forced us to move all
 * slot interaction logic to custom network payloads (ModServerNetworking).
 */
public class WarehouseInteractionHandler {

    public static boolean handleClicked(AbstractContainerMenu menu, int slotId, int button, Object clickType,
            Player player) {
        
        // Exclude creative mode inventory interactions to prevent conflicts
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu && player.getAbilities().instabuild) {
            return false;
        }

        // Ensure slotId is within valid bounds to prevent IndexOutOfBounds
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }

        Slot slot = menu.slots.get(slotId);
        
        // If it's a warehouse slot or an upgrade slot, we MUST cancel the vanilla click packet.
        // Because mods like Trinkets shift absolute slot IDs, Vanilla's slot packet is unreliable.
        // Instead, WarehouseWidget intercepts the mouse click and sends C2SClickWarehouseSlotPayload,
        // which is processed safely on the server side in ModServerNetworking.handleClickWarehouseSlot.
        if (slot.container instanceof PlayerWarehouse || slot instanceof com.portablestorage.upgrade.UpgradeSlot) {
            return true; // Return true to cancel the Vanilla interaction
        }
        
        return false; // Let vanilla handle normal inventory slots
    }
}