package com.portable.storage.event;

import com.portable.storage.PortableStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * 临时床事件处理器
 */
public class TempBedEventHandler {
    
    public static void register() {
        // 注册服务器tick事件，每tick检查临时床清理
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                TempBedCleanupHandler.tick(server);
            } catch (Exception e) {
                PortableStorage.LOGGER.error("Error in temp bed cleanup tick", e);
            }
        });
        
        PortableStorage.LOGGER.info("Temp bed event handler registered");
    }
}
