package com.portablestorage.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.portablestorage.client.gui.WarehouseScreen;
import com.portablestorage.client.gui.WarehouseWidget;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.CharacterEvent;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void portablestorage$charTypedWarehouseSearchFirst(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof WarehouseScreen s))
            return;
        WarehouseWidget w = s.portablestorage$getWarehouseWidget();
        if (w != null && w.tryConsumeCharForSearch(event))
            cir.setReturnValue(true);
    }
}
