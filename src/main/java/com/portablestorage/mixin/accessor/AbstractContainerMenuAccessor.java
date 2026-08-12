package com.portablestorage.mixin.accessor;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Invoker("moveItemStackTo")
    boolean invokeMoveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverse);

    @Invoker("addSlot")
    Slot invokeAddSlot(Slot slot);
}