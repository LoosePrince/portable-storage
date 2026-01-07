package com.portablestorage.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int portablestorage$getLeftPos();

    @Accessor("leftPos")
    void portablestorage$setLeftPos(int leftPos);

    @Accessor("topPos")
    int portablestorage$getTopPos();

    @Accessor("topPos")
    void portablestorage$setTopPos(int topPos);

    @Accessor("imageHeight")
    void portablestorage$setImageHeight(int imageHeight);

    @Accessor("hoveredSlot")
    net.minecraft.world.inventory.Slot portablestorage$getHoveredSlot();
}

