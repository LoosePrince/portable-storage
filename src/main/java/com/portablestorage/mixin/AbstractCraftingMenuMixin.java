package com.portablestorage.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 在父类 AbstractCraftingMenu 层拦截，将 InventoryMenu 的工作台从 2x2 扩展为 3x3。
 * 仅对 InventoryMenu 实例生效，不影响普通工作台。
 */
@Mixin(value = AbstractCraftingMenu.class, priority = 1000)
public abstract class AbstractCraftingMenuMixin {

    /**
     * 当 InventoryMenu 创建工作台容器时，强制使用 3x3 尺寸。
     */
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/inventory/TransientCraftingContainer"))
    private TransientCraftingContainer expandCraftingContainerForInventory(AbstractContainerMenu menu, int width, int height) {
        if ((Object) this instanceof InventoryMenu) {
            return new TransientCraftingContainer(menu, 3, 3);
        }
        return new TransientCraftingContainer(menu, width, height);
    }

    /**
     * 当 InventoryMenu 向 2x2 工作台添加槽位时，将下标从 {0,1,2,3} 重映射到
     * {0,1,3,4}，留出 3x3 容器的第 2/5/6/7/8 号格给 injectCraftingSlots 填充。
     */
    @Redirect(method = "addCraftingGridSlots", at = @At(value = "NEW", target = "net/minecraft/world/inventory/Slot"))
    private Slot remapCraftingSlotsForInventory(Container container, int index, int x, int y) {
        if ((Object) this instanceof InventoryMenu && container instanceof CraftingContainer) {
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
