package com.portable.storage.mixin.client;

import com.portable.storage.PortableStorage;
import com.portable.storage.client.ClientUpgradeState;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在有工作台升级时禁用配方书按钮点击
 */
@Mixin(RecipeBookWidget.class)
public class RecipeBookWidgetMixin {

    @Inject(at = @At("HEAD"), method = "toggleOpen", cancellable = true)
    private void portableStorage$onToggleOpen(CallbackInfo ci) {
        // 检查是否有工作台升级且未禁用
        boolean hasCraftingTableUpgrade = ClientUpgradeState.isCraftingTableUpgradeActive();
        PortableStorage.LOGGER.debug("RecipeBookWidgetMixin: toggleOpen called, hasCraftingTableUpgrade={}", hasCraftingTableUpgrade);
        
        if (hasCraftingTableUpgrade) {
            // 有工作台升级时，取消配方书按钮的点击事件
            PortableStorage.LOGGER.debug("RecipeBookWidgetMixin: Cancelling recipe book toggle due to crafting table upgrade");
            ci.cancel();
        }
    }
}