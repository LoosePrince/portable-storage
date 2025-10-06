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

        // 若用户刚点击了“切换原版”，跳过本次替换
        if (ScreenSwapBypass.consumeSkipNextCraftingSwap()) return;

        // 检查是否拥有“工作台升级”且未禁用
        boolean hasUpgrade = false;
        for (int i = 0; i < 5; i++) {
            var stack = ClientUpgradeState.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.CRAFTING_TABLE && !ClientUpgradeState.isSlotDisabled(i)) {
                hasUpgrade = true;
                break;
            }
        }
        if (!hasUpgrade) return;

        CraftingScreen crafting = (CraftingScreen) newScreen;
        var vanillaHandler = crafting.getScreenHandler();

        // 直接从客户端玩家获取背包，从 Screen 获取标题
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.player == null) return;
        PlayerInventory inv = mc.player.getInventory();
        Text title = newScreen.getTitle();

        // 仅替换界面为我们的自定义屏幕，但保持服务端 handler 不变，防止不同步
        mc.setScreen(new PortableCraftingScreen(new com.portable.storage.screen.PortableCraftingScreenHandler(vanillaHandler.syncId, inv), inv, title));
    }
}


