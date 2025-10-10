package com.portable.storage.mixin.client;

import com.portable.storage.client.ClientUpgradeState;
import com.portable.storage.client.ScreenSwapBypass;
import com.portable.storage.client.screen.PortableCraftingScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
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

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void portableStorage$onSetScreen(Screen newScreen, CallbackInfo ci) {
        // 当离开背包界面（newScreen 不是 InventoryScreen 且旧屏幕是 InventoryScreen）时，返还虚拟合成槽物品
        MinecraftClient self = (MinecraftClient)(Object)this;
        Screen old = self.currentScreen;
        if (old instanceof InventoryScreen && !(newScreen instanceof InventoryScreen)) {
            if (com.portable.storage.client.ClientStorageState.isStorageEnabled()) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.portable.storage.net.payload.OverlayCraftingClickC2SPayload(-1, 0, false)
                );
            }
        }
    }
}


