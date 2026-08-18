package com.portablestorage.mixin;

import com.portablestorage.util.CompatibilityDebug;
import com.portablestorage.util.FakePlayerUtils;
import com.portablestorage.util.InventoryMenuHelper;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AbstractCraftingMenu.class, priority = 1000)
public abstract class AbstractCraftingMenuMixin {

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/inventory/TransientCraftingContainer"))
    private TransientCraftingContainer expandCraftingContainerForInventory(AbstractContainerMenu menu, int width, int height) {
        if ((Object) this instanceof InventoryMenu) {
            Player owner = InventoryMenuHelper.CURRENT_INVENTORY_OWNER.get();
            CompatibilityDebug.log("crafting", () -> "InventoryMenu crafting container redirect; capturedOwner="
                    + (owner == null ? "none" : owner.getClass().getName()) + "; requested=" + width + "x" + height
                    + "; redirected=3x3");
            if (owner != null && FakePlayerUtils.isFakePlayer(owner)) {
                return new TransientCraftingContainer(menu, width, height);
            }
            return new TransientCraftingContainer(menu, 3, 3);
        }
        return new TransientCraftingContainer(menu, width, height);
    }

    @Redirect(method = "addCraftingGridSlots", at = @At(value = "NEW", target = "net/minecraft/world/inventory/Slot"))
    private Slot remapCraftingSlotsForInventory(Container container, int index, int x, int y) {
        if ((Object) this instanceof InventoryMenu && container instanceof CraftingContainer) {
            Player owner = InventoryMenuHelper.CURRENT_INVENTORY_OWNER.get();
            if (owner != null && FakePlayerUtils.isFakePlayer(owner)) {
                return new Slot(container, index, x, y);
            }
            int newIndex = switch (index) {
                case 2 -> 3;
                case 3 -> 4;
                default -> index;
            };
            return new Slot(container, newIndex, x, y);
        }
        return new Slot(container, index, x, y);
    }
}