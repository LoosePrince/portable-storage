package com.portablestorage.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class YACLConfig {
    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("gui.portablestorage.settings.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("gui.portablestorage.settings.tab.client"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.offset_inventory"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.offset_inventory.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.offsetInventory,
                                        val -> {
                                            ModConfig.offsetInventory = val;
                                            if (val) ModConfig.hideRecipeBook = true;
                                        }
                                )
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.hide_recipe_book"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.hide_recipe_book.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.hideRecipeBook,
                                        val -> ModConfig.hideRecipeBook = val
                                )
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("gui.portablestorage.settings.tab.server"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.enable_3x3_crafting"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.enable_3x3_crafting.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.is3x3Enabled(),
                                        val -> {} // 只读
                                )
                                .controller(BooleanControllerBuilder::create)
                                .available(false)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.drop_storage_on_death"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.drop_storage_on_death.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.dropStorageOnDeath,
                                        val -> {} // 只读
                                )
                                .controller(BooleanControllerBuilder::create)
                                .available(false)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.max_storage_types"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(-1, () -> ModConfig.maxStorageTypes, val -> {})
                                .controller(IntegerFieldControllerBuilder::create)
                                .available(false)
                                .build())
                        .option(Option.<Long>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.max_item_stack_size"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(-1L, () -> ModConfig.maxItemStackSize, val -> {})
                                .controller(LongFieldControllerBuilder::create)
                                .available(false)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.base_max_storage_types"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(54, () -> ModConfig.baseMaxStorageTypes, val -> {})
                                .controller(IntegerFieldControllerBuilder::create)
                                .available(false)
                                .build())
                        .option(Option.<Long>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.base_max_item_stack_size"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(-1L, () -> ModConfig.baseMaxItemStackSize, val -> {})
                                .controller(LongFieldControllerBuilder::create)
                                .available(false)
                                .build())
                        .build())
                .save(ModConfig::save)
                .build()
                .generateScreen(parent);
    }
}

