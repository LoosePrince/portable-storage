package com.portable.storage;

import net.fabricmc.api.ClientModInitializer;
import com.portable.storage.client.ClientConfig;
import com.portable.storage.client.ClientNetworkingHandlers;
import com.portable.storage.client.ModernUiCompat;
import com.portable.storage.client.screen.PortableCraftingScreen;
import com.portable.storage.client.event.ScreenEventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;

public class PortableStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 加载客户端配置
		ClientConfig.load();
		
		ClientNetworkingHandlers.register();
		// 注册界面事件处理器
		ScreenEventHandler.register();
		// 将自定义 ScreenHandlerType 绑定到自定义屏幕（使用原版 HandledScreens）
		HandledScreens.register(PortableStorage.PORTABLE_CRAFTING_HANDLER, PortableCraftingScreen::new);
		if (ModernUiCompat.isLoaded()) {
			ClientTickEvents.END_CLIENT_TICK.register(client -> ModernUiCompat.forceTooltipShadowRadiusZero());
		}

        // 绑定木桶：在物品提示中显示绑定信息
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            try {
                if (stack != null && stack.getItem() == Items.BARREL) {
                    NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
                    if (comp != null) {
                        NbtCompound nbt = comp.copyNbt();
                        if (nbt.contains("ps_owner_uuid_most") && nbt.contains("ps_owner_uuid_least")) {
                            String name = nbt.contains("ps_owner_name") ? nbt.getString("ps_owner_name") : "?";
                            java.util.UUID uuid = new java.util.UUID(nbt.getLong("ps_owner_uuid_most"), nbt.getLong("ps_owner_uuid_least"));
                            lines.add(Text.translatable("portable_storage.tooltip.bound_to", name, uuid.toString()).formatted(Formatting.GRAY));
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
	}
}