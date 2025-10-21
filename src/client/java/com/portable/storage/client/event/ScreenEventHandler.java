package com.portable.storage.client.event;

import com.portable.storage.sync.PlayerViewState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.Screen;

/**
 * 处理客户端界面事件
 */
public class ScreenEventHandler {
    private static Screen lastScreen = null;
    
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen currentScreen = client.currentScreen;
            
            // 检测界面关闭
            if (lastScreen != null && currentScreen == null) {
                // 界面从有变为无，说明界面被关闭了
                String screenName = lastScreen.getClass().getSimpleName();
                if (screenName.equals("InventoryScreen") || screenName.equals("CraftingScreen")) {
                    // 标记停止查看仓库界面
                    if (client.player != null) {
                        PlayerViewState.stopViewing(client.player.getUuid());
                    }
                }
            }
            
            lastScreen = currentScreen;
        });
    }
}
