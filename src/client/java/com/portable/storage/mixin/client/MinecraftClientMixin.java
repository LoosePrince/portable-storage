package com.portable.storage.mixin.client;

import com.portable.storage.client.ClientUpgradeState;
import com.portable.storage.client.ScreenSwapBypass;
import com.portable.storage.client.screen.PortableCraftingScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import com.portable.storage.screen.PortableCraftingScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void portableStorage$replaceCraftingScreen(Screen newScreen, CallbackInfo ci) {
        if (!(newScreen instanceof CraftingScreen)) return;
        if (newScreen instanceof PortableCraftingScreen) return;

        // 禁用对原版工作台界面的替换，直接使用原版界面（通过 CraftingScreenMixin 叠加仓库与补充逻辑）
        return;
    }
}


