package com.portablestorage.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("font")
    Font portablestorage$getFont();

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & net.minecraft.client.gui.narration.NarratableEntry> T invokeAddRenderableWidget(T widget);
}

