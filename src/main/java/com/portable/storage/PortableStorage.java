package com.portable.storage;

import com.portable.storage.block.ModBlocks;
import com.portable.storage.blockentity.ModBlockEntities;
import com.portable.storage.config.ServerConfig;
import com.portable.storage.event.PlayerInteractEventHandler;
import com.portable.storage.event.PlayerJoinEventHandler;
import com.portable.storage.screen.PortableCraftingScreenHandler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.net.NetworkChannels;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.util.Identifier;

public class PortableStorage implements ModInitializer {
    public static final String MOD_ID = "portable-storage";
    public static net.minecraft.screen.ScreenHandlerType<PortableCraftingScreenHandler> PORTABLE_CRAFTING_HANDLER;

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// 加载服务端配置
		ServerConfig.getInstance().load();

		// 注册自定义方块和方块实体
		ModBlocks.register();
		ModBlockEntities.register();

        NetworkChannels.registerCodecs();

        // 注册自定义 ScreenHandlerType（用于 EMI 识别并使用我们的处理器）
        PORTABLE_CRAFTING_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "portable_crafting"),
                new net.minecraft.screen.ScreenHandlerType<>(PortableCraftingScreenHandler::new, FeatureSet.empty())
        );
		ServerNetworkingHandlers.register();
		
		// 注册玩家交互事件处理器
		PlayerInteractEventHandler.register();
		
		// 注册玩家加入事件处理器
		PlayerJoinEventHandler.register();
		
		LOGGER.info("Portable Storage initialized");
	}
}