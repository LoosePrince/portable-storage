package com.portablestorage.config;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.C2SUpdateWarehouseStatePayload;
import com.portablestorage.network.UpdateServerConfigPayload;
import com.portablestorage.util.StoragePosition;
import com.portablestorage.util.WarehouseSetting;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Optional;

public class YACLConfig {
    private static PlayerWarehouse getWarehouse() {
        if (Minecraft.getInstance().player == null) return null;
        return ModComponents.get(Minecraft.getInstance().player).getWarehouse(Minecraft.getInstance().player.getUUID());
    }

    private static void updateSetting(WarehouseSetting setting, int value) {
        PlayerWarehouse warehouse = getWarehouse();
        if (warehouse != null) {
            switch (setting) {
                case SORT_MODE -> warehouse.setSortMode(value);
                case SORT_ORDER -> warehouse.setAscending(value == 1);
                case QUICK_INTERACTION -> warehouse.setQuickInteraction(value == 1);
                case SMART_COLLAPSE -> warehouse.setSmartCollapse(value == 1);
                case CRAFT_REFILL -> warehouse.setCraftRefill(value == 1);
                case FOLD -> warehouse.setFolded(value == 1);
            }
            ClientPlayNetworking.send(new C2SUpdateWarehouseStatePayload(
                Optional.empty(), 
                Optional.empty(), 
                Optional.of(setting.ordinal()), 
                Optional.of(value), 
                Optional.empty(),
                Optional.empty()
            ));
        }
    }

    private static void updateServerConfig() {
        if (!ModConfig.allowHotReload) return;
        ClientPlayNetworking.send(new UpdateServerConfigPayload(
            ModConfig.enable3x3Crafting,
            ModConfig.dropStorageOnDeath,
            ModConfig.maxStorageTypes,
            ModConfig.maxItemStackSize,
            ModConfig.baseMaxStorageTypes,
            ModConfig.baseMaxItemStackSize,
            ModConfig.unconditionalWarehouse,
            ModConfig.hopperRange,
            ModConfig.hopperFrequency,
            ModConfig.riftUpgradeItem,
            ModConfig.riftChunkSize,
            ModConfig.enableRiftForcedLoading,
            ModConfig.riftForcedLoadingRange
        ));
    }

    private static boolean canEditServerConfig() {
        return ModConfig.allowHotReload && Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(4);
    }

    public static Screen createHopperFilterScreen(Screen parent, java.util.List<String> currentFilters) {
        java.util.List<String> filters = new java.util.ArrayList<>(currentFilters);
        
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("gui.portablestorage.hopper_filter.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("gui.portablestorage.hopper_filter.category"))
                        .option(ListOption.<String>createBuilder()
                                .name(Component.translatable("gui.portablestorage.hopper_filter.list"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.hopper_filter.list.desc")))
                                .binding(
                                        new java.util.ArrayList<>(),
                                        () -> filters,
                                        val -> {
                                            filters.clear();
                                            filters.addAll(val);
                                        }
                                )
                                .controller(StringControllerBuilder::create)
                                .initial("")
                                .build())
                        .build())
                .save(() -> {
                    ClientPlayNetworking.send(new com.portablestorage.network.C2SUpdateHopperFiltersPayload(new java.util.ArrayList<>(filters)));
                })
                .build()
                .generateScreen(parent);
    }

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("gui.portablestorage.settings.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("gui.portablestorage.settings.tab.client"))
                        .option(Option.<StoragePosition>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.storage_position"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.storage_position.desc")))
                                .binding(
                                        StoragePosition.BOTTOM,
                                        () -> ModConfig.storagePosition,
                                        val -> {
                                            ModConfig.storagePosition = val;
                                            if (val.isHorizontal()) ModConfig.hideRecipeBook = true;
                                        }
                                )
                                .controller(opt -> CyclingListControllerBuilder.<StoragePosition>create(opt)
                                        .values(Arrays.asList(StoragePosition.values()))
                                        .formatValue(v -> Component.translatable("gui.portablestorage.settings.storage_position." + v.name().toLowerCase())))
                                .build())
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
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.show_small_icons"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.show_small_icons.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.showSmallIcons,
                                        val -> ModConfig.showSmallIcons = val
                                )
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.remove_experimental_warning"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.remove_experimental_warning.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.removeExperimentalWarning,
                                        val -> ModConfig.removeExperimentalWarning = val
                                )
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.group.warehouse"))
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.sort_mode"))
                                        .binding(
                                                0,
                                                () -> getWarehouse() != null ? getWarehouse().getSortMode() : 0,
                                                val -> updateSetting(WarehouseSetting.SORT_MODE, val)
                                        )
                                        .controller(opt -> CyclingListControllerBuilder.<Integer>create(opt)
                                                .values(Arrays.asList(0, 1, 2, 3))
                                                .formatValue(v -> Component.translatable("gui.portablestorage.sort_mode." + v)))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.sort_order"))
                                        .binding(
                                                true,
                                                () -> getWarehouse() != null ? getWarehouse().isAscending() : true,
                                                val -> updateSetting(WarehouseSetting.SORT_ORDER, val ? 1 : 0)
                                        )
                                        .controller(opt -> CyclingListControllerBuilder.<Boolean>create(opt)
                                                .values(Arrays.asList(true, false))
                                                .formatValue(v -> Component.translatable(v ? "gui.portablestorage.order.ascending" : "gui.portablestorage.order.descending")))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.quick_interaction"))
                                        .binding(
                                                true,
                                                () -> getWarehouse() != null ? getWarehouse().isQuickInteraction() : true,
                                                val -> updateSetting(WarehouseSetting.QUICK_INTERACTION, val ? 1 : 0)
                                        )
                                        .controller(BooleanControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.smart_collapse"))
                                        .binding(
                                                false,
                                                () -> getWarehouse() != null ? getWarehouse().isSmartCollapse() : false,
                                                val -> updateSetting(WarehouseSetting.SMART_COLLAPSE, val ? 1 : 0)
                                        )
                                        .controller(BooleanControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.craft_refill"))
                                        .binding(
                                                true,
                                                () -> getWarehouse() != null ? getWarehouse().isCraftRefill() : true,
                                                val -> updateSetting(WarehouseSetting.CRAFT_REFILL, val ? 1 : 0)
                                        )
                                        .controller(BooleanControllerBuilder::create)
                                        .build())
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("gui.portablestorage.settings.tab.server"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.allow_hot_reload"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.allow_hot_reload.desc")))
                                .binding(
                                        false,
                                        () -> ModConfig.allowHotReload,
                                        val -> {} // 只读
                                )
                                .controller(BooleanControllerBuilder::create)
                                .available(false)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.enable_3x3_crafting"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.enable_3x3_crafting.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.is3x3Enabled(),
                                        val -> {
                                            ModConfig.enable3x3Crafting = val;
                                            ModConfig.setActive3x3Crafting(val);
                                            updateServerConfig();
                                        }
                                )
                                .controller(BooleanControllerBuilder::create)
                                .available(canEditServerConfig())
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.drop_storage_on_death"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.drop_storage_on_death.desc")))
                                .binding(
                                        true,
                                        () -> ModConfig.dropStorageOnDeath,
                                        val -> {
                                            ModConfig.dropStorageOnDeath = val;
                                            updateServerConfig();
                                        }
                                )
                                .controller(BooleanControllerBuilder::create)
                                .available(canEditServerConfig())
                                .build())
                        .option(Option.<String>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.unconditional_warehouse"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.unconditional_warehouse.desc")))
                                .binding(
                                        "NONE",
                                        () -> ModConfig.unconditionalWarehouse,
                                        val -> {
                                            ModConfig.unconditionalWarehouse = val;
                                            updateServerConfig();
                                        }
                                )
                                .controller(opt -> CyclingListControllerBuilder.<String>create(opt)
                                        .values(Arrays.asList("NONE", "BASE", "FULL"))
                                        .formatValue(v -> Component.translatable("gui.portablestorage.settings.unconditional_warehouse." + v.toLowerCase())))
                                .available(canEditServerConfig())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.group.hopper"))
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.hopper_range"))
                                        .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.hopper_range.desc")))
                                        .binding(
                                                5,
                                                () -> ModConfig.hopperRange,
                                                val -> {
                                                    ModConfig.hopperRange = val;
                                                    updateServerConfig();
                                                }
                                        )
                                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(2, 20))
                                        .available(canEditServerConfig())
                                        .build())
                                .option(Option.<Double>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.hopper_frequency"))
                                        .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.hopper_frequency.desc")))
                                        .binding(
                                                1.0,
                                                () -> ModConfig.hopperFrequency,
                                                val -> {
                                                    ModConfig.hopperFrequency = val;
                                                    updateServerConfig();
                                                }
                                        )
                                        .controller(opt -> dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder.create(opt).range(0.2, 5.0))
                                        .available(canEditServerConfig())
                                        .build())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.group.rift"))
                                .option(Option.<String>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.rift_upgrade_item"))
                                        .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.rift_upgrade_item.desc")))
                                        .binding(
                                                "minecraft:dragon_egg",
                                                () -> ModConfig.riftUpgradeItem,
                                                val -> {
                                                    ModConfig.riftUpgradeItem = val;
                                                    updateServerConfig();
                                                }
                                        )
                                        .controller(StringControllerBuilder::create)
                                        .available(canEditServerConfig())
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.rift_chunk_size"))
                                        .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.rift_chunk_size.desc")))
                                        .binding(
                                                1,
                                                () -> ModConfig.riftChunkSize,
                                                val -> {
                                                    ModConfig.riftChunkSize = val;
                                                    updateServerConfig();
                                                }
                                        )
                                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 10))
                                        .available(canEditServerConfig())
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.enable_rift_forced_loading"))
                                        .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.enable_rift_forced_loading.desc")))
                                        .binding(
                                                true,
                                                () -> ModConfig.enableRiftForcedLoading,
                                                val -> {
                                                    ModConfig.enableRiftForcedLoading = val;
                                                    updateServerConfig();
                                                }
                                        )
                                        .controller(BooleanControllerBuilder::create)
                                        .available(canEditServerConfig())
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("gui.portablestorage.settings.rift_forced_loading_range"))
                                        .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.rift_forced_loading_range.desc")))
                                        .binding(
                                                1,
                                                () -> ModConfig.riftForcedLoadingRange,
                                                val -> {
                                                    ModConfig.riftForcedLoadingRange = val;
                                                    updateServerConfig();
                                                }
                                        )
                                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 5))
                                        .available(canEditServerConfig())
                                        .build())
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.max_storage_types"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(
                                        -1, 
                                        () -> ModConfig.maxStorageTypes, 
                                        val -> {
                                            ModConfig.maxStorageTypes = val;
                                            updateServerConfig();
                                        }
                                )
                                .controller(IntegerFieldControllerBuilder::create)
                                .available(canEditServerConfig())
                                .build())
                        .option(Option.<Long>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.max_item_stack_size"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(
                                        -1L, 
                                        () -> ModConfig.maxItemStackSize, 
                                        val -> {
                                            ModConfig.maxItemStackSize = val;
                                            updateServerConfig();
                                        }
                                )
                                .controller(LongFieldControllerBuilder::create)
                                .available(canEditServerConfig())
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.base_max_storage_types"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(
                                        54, 
                                        () -> ModConfig.baseMaxStorageTypes, 
                                        val -> {
                                            ModConfig.baseMaxStorageTypes = val;
                                            updateServerConfig();
                                        }
                                )
                                .controller(IntegerFieldControllerBuilder::create)
                                .available(canEditServerConfig())
                                .build())
                        .option(Option.<Long>createBuilder()
                                .name(Component.translatable("gui.portablestorage.settings.base_max_item_stack_size"))
                                .description(OptionDescription.of(Component.translatable("gui.portablestorage.settings.server_limit.desc")))
                                .binding(
                                        -1L, 
                                        () -> ModConfig.baseMaxItemStackSize, 
                                        val -> {
                                            ModConfig.baseMaxItemStackSize = val;
                                            updateServerConfig();
                                        }
                                )
                                .controller(LongFieldControllerBuilder::create)
                                .available(canEditServerConfig())
                                .build())
                        .build())
                .save(ModConfig::save)
                .build()
                .generateScreen(parent);
    }
}

