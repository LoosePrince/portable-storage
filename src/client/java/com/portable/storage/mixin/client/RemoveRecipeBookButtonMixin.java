package com.portable.storage.mixin.client;

import com.portable.storage.client.ClientUpgradeState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(InventoryScreen.class)
public abstract class RemoveRecipeBookButtonMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void portableStorage$removeRecipeBookButton(CallbackInfo ci) {
        if (ClientUpgradeState.isCraftingTableUpgradeActive()) {
            InventoryScreen self = (InventoryScreen)(Object)this;
            portableStorage$removeRecipeBookButtonInternal(self);
        }
    }
    
    @Inject(method = "render", at = @At("HEAD"))
    private void portableStorage$removeRecipeBookButtonOnRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ClientUpgradeState.isCraftingTableUpgradeActive()) {
            InventoryScreen self = (InventoryScreen)(Object)this;
            portableStorage$removeRecipeBookButtonInternal(self);
        }
    }
    
    private void portableStorage$removeRecipeBookButtonInternal(InventoryScreen self) {
        ClickableWidget recipeBookButton = null;
        for (Element child : self.children()) {
            if (child instanceof ClickableWidget widget && widget instanceof net.minecraft.client.gui.widget.TexturedButtonWidget) {
                recipeBookButton = widget;
                break;
            }
        }
        
        if (recipeBookButton != null) {
            try {
                java.lang.reflect.Field childrenField = net.minecraft.client.gui.screen.Screen.class.getDeclaredField("children");
                childrenField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Element> children = (List<Element>) childrenField.get(self);
                children.remove(recipeBookButton);
                
                java.lang.reflect.Field drawablesField = net.minecraft.client.gui.screen.Screen.class.getDeclaredField("drawables");
                drawablesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Drawable> drawables = (List<Drawable>) drawablesField.get(self);
                drawables.remove(recipeBookButton);
                
                java.lang.reflect.Field selectablesField = net.minecraft.client.gui.screen.Screen.class.getDeclaredField("selectables");
                selectablesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Selectable> selectables = (List<Selectable>) selectablesField.get(self);
                selectables.remove(recipeBookButton);
            } catch (Exception e) {
                com.portable.storage.PortableStorage.LOGGER.error("Failed to remove recipe book button", e);
            }
        }
    }
}

