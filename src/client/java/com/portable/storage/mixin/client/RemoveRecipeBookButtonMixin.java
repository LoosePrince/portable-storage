package com.portable.storage.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portable.storage.PortableStorage;
import com.portable.storage.client.ClientUpgradeState;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ClickableWidget;

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
        
        // 尝试通过类名精确匹配
        for (Element child : self.children()) {
            if (child instanceof ClickableWidget widget && widget instanceof net.minecraft.client.gui.widget.TexturedButtonWidget) {
                recipeBookButton = widget;
                PortableStorage.LOGGER.debug("找到配方书按钮: 类名={}, 大小={}x{}", 
                    widget.getClass().getSimpleName(), widget.getWidth(), widget.getHeight());
                break;
            }
        }
        
        if (recipeBookButton != null) {
            // 直接隐藏按钮
            try {
                if (recipeBookButton instanceof net.minecraft.client.gui.widget.ClickableWidget widget) {
                    widget.visible = false;
                    widget.active = false;
                    PortableStorage.LOGGER.debug("成功隐藏配方书按钮: 类名={}", recipeBookButton.getClass().getSimpleName());
                }
            } catch (Exception e) {
                PortableStorage.LOGGER.error("隐藏配方书按钮失败: {}", e.getMessage());
            }
        }
    }
}

