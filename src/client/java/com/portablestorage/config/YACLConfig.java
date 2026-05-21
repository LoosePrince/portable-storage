package com.portablestorage.config;

import java.util.Arrays;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.client.gui.WarehouseStateSync;
import com.portablestorage.network.UpdateServerConfigPayload;
import com.portablestorage.util.StoragePosition;
import com.portablestorage.util.WarehouseSetting;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class YACLConfig {
        private static PlayerWarehouse getWarehouse() {
                if (Minecraft.getInstance().player == null)
                        return null;
                return ModComponents.get(Minecraft.getInstance().player)
                                .getWarehouse(Minecraft.getInstance().player.getUUID());
        }

        private static void updateSetting(WarehouseSetting setting, int value) {
                PlayerWarehouse warehouse = getWarehouse();
                if (warehouse != null) {
                        WarehouseStateSync.applySetting(warehouse, setting, value);
                        WarehouseStateSync.sendSetting(setting, value);
                }
        }

        private static void updateServerConfig() {
                if (!ModConfig.allowHotReload)
                        return;
                // 确保已经向服务端发起过一次权限请求（结果会缓存到 ModClientNetworking）
                com.portablestorage.network.ModClientNetworking.requestConfigPermission();
                ClientPlayNetworking.send(new UpdateServerConfigPayload(
                                ModConfig.enable3x3Crafting,
                                ModConfig.dropStorageOnDeath,
                                ModConfig.maxStorageTypes,
                                ModConfig.maxItemStackSize,
                                ModConfig.baseMaxStorageTypes,
                                ModConfig.baseMaxItemStackSize,
                                ModConfig.maxItemNbtSize,
                                ModConfig.unconditionalWarehouse,
                                ModConfig.baseWarehouseActivationItem,
                                ModConfig.fullWarehouseActivationItem,
                                ModConfig.hopperRange,
                                ModConfig.hopperFrequency,
                                ModConfig.lavaInfiniteThreshold,
                                ModConfig.waterInfiniteThreshold,
                                ModConfig.riftUpgradeItem,
                                ModConfig.riftChunkSize,
                                ModConfig.riftPlotSpacingChunks,
                                ModConfig.riftFloorY,
                                ModConfig.enableRiftForcedLoading,
                                ModConfig.riftForcedLoadingRange,
                                ModConfig.enableRiftAvatar,
                                ModConfig.enableRiftBorder,
                                ModConfig.riftBorderWarningBlocks,
                                ModConfig.enableConduitUpgrade));
        }

        private static boolean canEditServerConfig() {
                Minecraft mc = Minecraft.getInstance();
                // 不在存档或服务器中（如主菜单通过 Mod Menu 打开配置）时视为有权限，仅修改本地配置文件
                if (mc.level == null) {
                        return true;
                }
                if (!ModConfig.allowHotReload || mc.player == null) {
                        return false;
                }
                // 最终权限由服务端判定，这里只依赖服务端返回的缓存结果
                return com.portablestorage.network.ModClientNetworking.canEditServerConfig();
        }

        public static Screen createHopperFilterScreen(Screen parent, java.util.List<String> currentFilters,
                        boolean isBlacklist) {
                java.util.List<String> filters = new java.util.ArrayList<>(currentFilters);
                final boolean[] blacklist = { isBlacklist };

                return YetAnotherConfigLib.createBuilder()
                                .title(Component.translatable("gui.portablestorage.hopper_filter.title"))
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.translatable(
                                                                "gui.portablestorage.hopper_filter.category"))
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.filter_mode"))
                                                                .binding(
                                                                                true,
                                                                                () -> blacklist[0],
                                                                                val -> blacklist[0] = val)
                                                                .controller(opt -> CyclingListControllerBuilder
                                                                                .<Boolean>create(opt)
                                                                                .values(Arrays.asList(true, false))
                                                                                .formatValue(v -> Component
                                                                                                .translatable(v ? "gui.portablestorage.filter_mode.blacklist"
                                                                                                                : "gui.portablestorage.filter_mode.whitelist")))
                                                                .build())
                                                .option(ListOption.<String>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.hopper_filter.list"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.hopper_filter.list.desc")))
                                                                .binding(
                                                                                new java.util.ArrayList<>(),
                                                                                () -> filters,
                                                                                val -> {
                                                                                        filters.clear();
                                                                                        filters.addAll(val);
                                                                                })
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .build())
                                .save(() -> {
                                        ClientPlayNetworking.send(
                                                        new com.portablestorage.network.C2SUpdateHopperFiltersPayload(
                                                                        new java.util.ArrayList<>(filters),
                                                                        blacklist[0]));
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
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.storage_position"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.storage_position.desc")))
                                                                .binding(
                                                                                StoragePosition.BOTTOM,
                                                                                () -> ModConfig.storagePosition,
                                                                                val -> {
                                                                                        ModConfig.storagePosition = val;
                                                                                        if (val.isHorizontal())
                                                                                                ModConfig.hideRecipeBook = true;
                                                                                })
                                                                .controller(opt -> CyclingListControllerBuilder
                                                                                .<StoragePosition>create(opt)
                                                                                .values(Arrays.asList(StoragePosition
                                                                                                .values()))
                                                                                .formatValue(v -> Component
                                                                                                .translatable("gui.portablestorage.settings.storage_position."
                                                                                                                + v.name().toLowerCase())))
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.offset_inventory"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.offset_inventory.desc")))
                                                                .binding(
                                                                                true,
                                                                                () -> ModConfig.offsetInventory,
                                                                                val -> {
                                                                                        ModConfig.offsetInventory = val;
                                                                                        if (val)
                                                                                                ModConfig.hideRecipeBook = true;
                                                                                })
                                                                .controller(BooleanControllerBuilder::create)
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.hide_recipe_book"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.hide_recipe_book.desc")))
                                                                .binding(
                                                                                true,
                                                                                () -> ModConfig.hideRecipeBook,
                                                                                val -> {
                                                                                        // 当偏移背包界面或采用横向布局时，始终强制隐藏配方书按钮
                                                                                        if (ModConfig.storagePosition
                                                                                                        .isHorizontal()
                                                                                                        || ModConfig.offsetInventory) {
                                                                                                ModConfig.hideRecipeBook = true;
                                                                                        } else {
                                                                                                ModConfig.hideRecipeBook = val;
                                                                                        }
                                                                                })
                                                                .controller(BooleanControllerBuilder::create)
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.show_small_icons"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.show_small_icons.desc")))
                                                                .binding(
                                                                                true,
                                                                                () -> ModConfig.showSmallIcons,
                                                                                val -> ModConfig.showSmallIcons = val)
                                                                .controller(BooleanControllerBuilder::create)
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.remove_experimental_warning"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.remove_experimental_warning.desc")))
                                                                .binding(
                                                                                true,
                                                                                () -> ModConfig.removeExperimentalWarning,
                                                                                val -> ModConfig.removeExperimentalWarning = val)
                                                                .controller(BooleanControllerBuilder::create)
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.auto_fold_on_close"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.auto_fold_on_close.desc")))
                                                                .binding(
                                                                                false,
                                                                                () -> ModConfig.autoFoldOnClose,
                                                                                val -> ModConfig.autoFoldOnClose = val)
                                                                .controller(BooleanControllerBuilder::create)
                                                                .build())
                                                .group(OptionGroup.createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.group.warehouse"))
                                                                .option(Option.<Integer>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.sort_mode"))
                                                                                .description(OptionDescription
                                                                                                .of(Component
                                                                                                                .translatable("gui.portablestorage.settings.sort_mode.desc")))
                                                                                .binding(
                                                                                                0,
                                                                                                () -> getWarehouse() != null
                                                                                                                ? getWarehouse().getSortMode()
                                                                                                                : 0,
                                                                                                val -> updateSetting(
                                                                                                                WarehouseSetting.SORT_MODE,
                                                                                                                val))
                                                                                .controller(opt -> CyclingListControllerBuilder
                                                                                                .<Integer>create(opt)
                                                                                                .values(Arrays.asList(0,
                                                                                                                1, 2,
                                                                                                                3))
                                                                                                .formatValue(v -> Component
                                                                                                                .translatable("gui.portablestorage.sort_mode."
                                                                                                                                + v)))
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.sort_order"))
                                                                                .description(OptionDescription
                                                                                                .of(Component
                                                                                                                .translatable("gui.portablestorage.settings.sort_order.desc")))
                                                                                .binding(
                                                                                                true,
                                                                                                () -> getWarehouse() != null
                                                                                                                ? getWarehouse().isAscending()
                                                                                                                : true,
                                                                                                val -> updateSetting(
                                                                                                                WarehouseSetting.SORT_ORDER,
                                                                                                                val ? 1
                                                                                                                                : 0))
                                                                                .controller(opt -> CyclingListControllerBuilder
                                                                                                .<Boolean>create(opt)
                                                                                                .values(Arrays.asList(
                                                                                                                true,
                                                                                                                false))
                                                                                                .formatValue(v -> Component
                                                                                                                .translatable(v ? "gui.portablestorage.order.ascending"
                                                                                                                                : "gui.portablestorage.order.descending")))
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.quick_interaction"))
                                                                                .description(OptionDescription
                                                                                                .of(Component
                                                                                                                .translatable("gui.portablestorage.settings.quick_interaction.desc")))
                                                                                .binding(
                                                                                                true,
                                                                                                () -> getWarehouse() != null
                                                                                                                ? getWarehouse().isQuickInteraction()
                                                                                                                : true,
                                                                                                val -> updateSetting(
                                                                                                                WarehouseSetting.QUICK_INTERACTION,
                                                                                                                val ? 1
                                                                                                                                : 0))
                                                                                .controller(BooleanControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.smart_collapse"))
                                                                                .description(OptionDescription
                                                                                                .of(Component
                                                                                                                .translatable("gui.portablestorage.settings.smart_collapse.desc")))
                                                                                .binding(
                                                                                                false,
                                                                                                () -> getWarehouse() != null
                                                                                                                ? getWarehouse().isSmartCollapse()
                                                                                                                : false,
                                                                                                val -> updateSetting(
                                                                                                                WarehouseSetting.SMART_COLLAPSE,
                                                                                                                val ? 1
                                                                                                                                : 0))
                                                                                .controller(BooleanControllerBuilder::create)
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.craft_refill"))
                                                                                .description(OptionDescription
                                                                                                .of(Component
                                                                                                                .translatable("gui.portablestorage.settings.craft_refill.desc")))
                                                                                .binding(
                                                                                                true,
                                                                                                () -> getWarehouse() != null
                                                                                                                ? getWarehouse().isCraftRefill()
                                                                                                                : true,
                                                                                                val -> updateSetting(
                                                                                                                WarehouseSetting.CRAFT_REFILL,
                                                                                                                val ? 1
                                                                                                                                : 0))
                                                                                .controller(BooleanControllerBuilder::create)
                                                                                .build())
                                                                .build())
                                                .build())
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.translatable("gui.portablestorage.settings.tab.server"))
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.allow_hot_reload"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.allow_hot_reload.desc")))
                                                                .binding(
                                                                                false,
                                                                                () -> ModConfig.allowHotReload,
                                                                                val -> {
                                                                                } // 只读
                                                                )
                                                                .controller(BooleanControllerBuilder::create)
                                                                .available(false)
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.enable_3x3_crafting"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.enable_3x3_crafting.desc")))
                                                                .binding(
                                                                                true,
                                                                                () -> ModConfig.is3x3Enabled(),
                                                                                val -> {
                                                                                        ModConfig.enable3x3Crafting = val;
                                                                                        ModConfig.setActive3x3Crafting(
                                                                                                        val);
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(BooleanControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.drop_storage_on_death"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.drop_storage_on_death.desc")))
                                                                .binding(
                                                                                true,
                                                                                () -> ModConfig.dropStorageOnDeath,
                                                                                val -> {
                                                                                        ModConfig.dropStorageOnDeath = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(BooleanControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.enable_conduit_upgrade"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.enable_conduit_upgrade.desc")))
                                                                .binding(
                                                                                true,
                                                                                () -> ModConfig.enableConduitUpgrade,
                                                                                val -> {
                                                                                        ModConfig.enableConduitUpgrade = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(BooleanControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .option(Option.<String>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.unconditional_warehouse"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.unconditional_warehouse.desc")))
                                                                .binding(
                                                                                "NONE",
                                                                                () -> ModConfig.unconditionalWarehouse,
                                                                                val -> {
                                                                                        ModConfig.unconditionalWarehouse = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(opt -> CyclingListControllerBuilder
                                                                                .<String>create(opt)
                                                                                .values(Arrays.asList("NONE", "BASE",
                                                                                                "FULL"))
                                                                                .formatValue(v -> Component
                                                                                                .translatable("gui.portablestorage.settings.unconditional_warehouse."
                                                                                                                + v.toLowerCase())))
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .group(OptionGroup.createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.group.warehouse_activation"))
                                                                .option(Option.<String>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.base_warehouse_activation_item"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.base_warehouse_activation_item.desc")))
                                                                                .binding(
                                                                                                "minecraft:heart_of_the_sea",
                                                                                                () -> ModConfig.baseWarehouseActivationItem,
                                                                                                val -> {
                                                                                                        ModConfig.baseWarehouseActivationItem = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(StringControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<String>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.full_warehouse_activation_item"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.full_warehouse_activation_item.desc")))
                                                                                .binding(
                                                                                                "minecraft:nether_star",
                                                                                                () -> ModConfig.fullWarehouseActivationItem,
                                                                                                val -> {
                                                                                                        ModConfig.fullWarehouseActivationItem = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(StringControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .build())
                                                .group(OptionGroup.createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.group.hopper"))
                                                                .option(Option.<Integer>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.hopper_range"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.hopper_range.desc")))
                                                                                .binding(
                                                                                                5,
                                                                                                () -> ModConfig.hopperRange,
                                                                                                val -> {
                                                                                                        ModConfig.hopperRange = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(opt -> IntegerFieldControllerBuilder
                                                                                                .create(opt)
                                                                                                .range(2, 20))
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Double>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.hopper_frequency"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.hopper_frequency.desc")))
                                                                                .binding(
                                                                                                1.0,
                                                                                                () -> ModConfig.hopperFrequency,
                                                                                                val -> {
                                                                                                        ModConfig.hopperFrequency = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(opt -> dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder
                                                                                                .create(opt)
                                                                                                .range(0.2, 5.0))
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .build())
                                                .group(OptionGroup.createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.group.fluid"))
                                                                .option(Option.<Long>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.lava_infinite_threshold"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.lava_infinite_threshold.desc")))
                                                                                .binding(
                                                                                                10000L,
                                                                                                () -> ModConfig.lavaInfiniteThreshold,
                                                                                                val -> {
                                                                                                        ModConfig.lavaInfiniteThreshold = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(LongFieldControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Long>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.water_infinite_threshold"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.water_infinite_threshold.desc")))
                                                                                .binding(
                                                                                                2L,
                                                                                                () -> ModConfig.waterInfiniteThreshold,
                                                                                                val -> {
                                                                                                        ModConfig.waterInfiniteThreshold = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(LongFieldControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .build())
                                                .group(OptionGroup.createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.group.rift"))
                                                                .option(Option.<String>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.rift_upgrade_item"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.rift_upgrade_item.desc")))
                                                                                .binding(
                                                                                                "minecraft:dragon_egg",
                                                                                                () -> ModConfig.riftUpgradeItem,
                                                                                                val -> {
                                                                                                        ModConfig.riftUpgradeItem = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(StringControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Integer>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.rift_chunk_size"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.rift_chunk_size.desc")))
                                                                                .binding(
                                                                                                1,
                                                                                                () -> ModConfig.riftChunkSize,
                                                                                                val -> {
                                                                                                        ModConfig.riftChunkSize = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(opt -> IntegerFieldControllerBuilder
                                                                                                .create(opt)
                                                                                                .range(1, 10))
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.enable_rift_forced_loading"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.enable_rift_forced_loading.desc")))
                                                                                .binding(
                                                                                                true,
                                                                                                () -> ModConfig.enableRiftForcedLoading,
                                                                                                val -> {
                                                                                                        ModConfig.enableRiftForcedLoading = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(BooleanControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Integer>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.rift_forced_loading_range"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.rift_forced_loading_range.desc")))
                                                                                .binding(
                                                                                                1,
                                                                                                () -> ModConfig.riftForcedLoadingRange,
                                                                                                val -> {
                                                                                                        ModConfig.riftForcedLoadingRange = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(opt -> IntegerFieldControllerBuilder
                                                                                                .create(opt)
                                                                                                .range(0, 5))
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Integer>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.rift_plot_spacing_chunks"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.rift_plot_spacing_chunks.desc")))
                                                                                .binding(
                                                                                                64,
                                                                                                () -> ModConfig.riftPlotSpacingChunks,
                                                                                                val -> {
                                                                                                        ModConfig.riftPlotSpacingChunks = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(opt -> IntegerFieldControllerBuilder
                                                                                                .create(opt)
                                                                                                .range(16, 1024))
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Integer>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.rift_floor_y"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.rift_floor_y.desc")))
                                                                                .binding(
                                                                                                64,
                                                                                                () -> ModConfig.riftFloorY,
                                                                                                val -> {
                                                                                                        ModConfig.riftFloorY = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(opt -> IntegerFieldControllerBuilder
                                                                                                .create(opt)
                                                                                                .range(-60, 300))
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.enable_rift_avatar"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.enable_rift_avatar.desc")))
                                                                                .binding(
                                                                                                true,
                                                                                                () -> ModConfig.enableRiftAvatar,
                                                                                                val -> {
                                                                                                        ModConfig.enableRiftAvatar = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(BooleanControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Boolean>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.enable_rift_border"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.enable_rift_border.desc")))
                                                                                .binding(
                                                                                                true,
                                                                                                () -> ModConfig.enableRiftBorder,
                                                                                                val -> {
                                                                                                        ModConfig.enableRiftBorder = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(BooleanControllerBuilder::create)
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .option(Option.<Integer>createBuilder()
                                                                                .name(Component.translatable(
                                                                                                "gui.portablestorage.settings.rift_border_warning_blocks"))
                                                                                .description(OptionDescription.of(
                                                                                                Component.translatable(
                                                                                                                "gui.portablestorage.settings.rift_border_warning_blocks.desc")))
                                                                                .binding(
                                                                                                0,
                                                                                                () -> ModConfig.riftBorderWarningBlocks,
                                                                                                val -> {
                                                                                                        ModConfig.riftBorderWarningBlocks = val;
                                                                                                        updateServerConfig();
                                                                                                })
                                                                                .controller(opt -> IntegerFieldControllerBuilder
                                                                                                .create(opt)
                                                                                                .range(0, 32))
                                                                                .available(canEditServerConfig())
                                                                                .build())
                                                                .build())
                                                .option(Option.<Integer>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.max_storage_types"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.server_limit.desc")))
                                                                .binding(
                                                                                -1,
                                                                                () -> ModConfig.maxStorageTypes,
                                                                                val -> {
                                                                                        ModConfig.maxStorageTypes = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(IntegerFieldControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .option(Option.<Long>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.max_item_stack_size"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.server_limit.desc")))
                                                                .binding(
                                                                                -1L,
                                                                                () -> ModConfig.maxItemStackSize,
                                                                                val -> {
                                                                                        ModConfig.maxItemStackSize = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(LongFieldControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .option(Option.<Integer>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.base_max_storage_types"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.server_limit.desc")))
                                                                .binding(
                                                                                54,
                                                                                () -> ModConfig.baseMaxStorageTypes,
                                                                                val -> {
                                                                                        ModConfig.baseMaxStorageTypes = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(IntegerFieldControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .option(Option.<Long>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.base_max_item_stack_size"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.server_limit.desc")))
                                                                .binding(
                                                                                -1L,
                                                                                () -> ModConfig.baseMaxItemStackSize,
                                                                                val -> {
                                                                                        ModConfig.baseMaxItemStackSize = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(LongFieldControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .option(Option.<Integer>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.settings.max_item_nbt_size"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.settings.max_item_nbt_size.desc")))
                                                                .binding(
                                                                                10240,
                                                                                () -> ModConfig.maxItemNbtSize,
                                                                                val -> {
                                                                                        ModConfig.maxItemNbtSize = val;
                                                                                        updateServerConfig();
                                                                                })
                                                                .controller(IntegerFieldControllerBuilder::create)
                                                                .available(canEditServerConfig())
                                                                .build())
                                                .build())
                                .save(ModConfig::save)
                                .build()
                                .generateScreen(parent);
        }

        public static Screen createFoodFilterScreen(Screen parent, java.util.List<String> currentFilters,
                        boolean isBlacklist) {
                java.util.List<String> filters = new java.util.ArrayList<>(currentFilters);
                final boolean[] blacklist = { isBlacklist };

                return YetAnotherConfigLib.createBuilder()
                                .title(Component.translatable("gui.portablestorage.food_filter.title"))
                                .category(ConfigCategory.createBuilder()
                                                .name(Component.translatable(
                                                                "gui.portablestorage.food_filter.category"))
                                                .option(Option.<Boolean>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.filter_mode"))
                                                                .binding(
                                                                                true,
                                                                                () -> blacklist[0],
                                                                                val -> blacklist[0] = val)
                                                                .controller(opt -> CyclingListControllerBuilder
                                                                                .<Boolean>create(opt)
                                                                                .values(Arrays.asList(true, false))
                                                                                .formatValue(v -> Component
                                                                                                .translatable(v ? "gui.portablestorage.filter_mode.blacklist"
                                                                                                                : "gui.portablestorage.filter_mode.whitelist")))
                                                                .build())
                                                .option(ListOption.<String>createBuilder()
                                                                .name(Component.translatable(
                                                                                "gui.portablestorage.food_filter.list"))
                                                                .description(OptionDescription.of(Component
                                                                                .translatable("gui.portablestorage.food_filter.list.desc")))
                                                                .binding(
                                                                                new java.util.ArrayList<>(),
                                                                                () -> filters,
                                                                                val -> {
                                                                                        filters.clear();
                                                                                        filters.addAll(val);
                                                                                })
                                                                .controller(StringControllerBuilder::create)
                                                                .initial("")
                                                                .build())
                                                .build())
                                .save(() -> {
                                        ClientPlayNetworking.send(
                                                        new com.portablestorage.network.C2SUpdateFoodFiltersPayload(
                                                                        new java.util.ArrayList<>(filters),
                                                                        blacklist[0]));
                                })
                                .build()
                                .generateScreen(parent);
        }

        public static Screen createSharingManagementScreen(Screen parent, PlayerWarehouse warehouse) {
                var builder = YetAnotherConfigLib.createBuilder()
                                .title(Component.translatable("gui.portablestorage.sharing_management.title"));

                ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
                                .name(Component.translatable("gui.portablestorage.sharing_management.title"));

                // 获取所有已知仓库（即所有曾经开启过仓库的玩家）
                var component = ModComponents.WAREHOUSE.get(Minecraft.getInstance().level.getScoreboard());
                java.util.List<PlayerWarehouse> allWarehouses = new java.util.ArrayList<>(component.getAllWarehouses());

                // 排序：在线优先，然后按名字排序
                allWarehouses.sort((a, b) -> {
                        boolean aOnline = Minecraft.getInstance().getConnection()
                                        .getPlayerInfo(a.getOwnerUuid()) != null;
                        boolean bOnline = Minecraft.getInstance().getConnection()
                                        .getPlayerInfo(b.getOwnerUuid()) != null;
                        if (aOnline != bOnline)
                                return aOnline ? -1 : 1;
                        return a.getOwnerName().compareToIgnoreCase(b.getOwnerName());
                });

                for (PlayerWarehouse pw : allWarehouses) {
                        if (pw.getOwnerUuid().equals(warehouse.getOwnerUuid()))
                                continue;

                        final java.util.UUID targetUuid = pw.getOwnerUuid();
                        boolean isOnline = Minecraft.getInstance().getConnection().getPlayerInfo(targetUuid) != null;
                        String name = pw.getOwnerName();

                        categoryBuilder.option(Option.<Boolean>createBuilder()
                                        .name(Component.literal(name)
                                                        .withStyle(isOnline ? net.minecraft.ChatFormatting.WHITE
                                                                        : net.minecraft.ChatFormatting.GRAY))
                                        .description(OptionDescription.of(Component.translatable(
                                                        "gui.portablestorage.sharing_management.toggle_hint")))
                                        .binding(
                                                        true,
                                                        () -> !warehouse.isForbidden(targetUuid),
                                                        val -> {
                                                                warehouse.setForbidden(targetUuid, !val);
                                                                ClientPlayNetworking.send(
                                                                                new com.portablestorage.network.C2SUpdateForbiddenPlayersPayload(
                                                                                                targetUuid, !val));
                                                        })
                                        .controller(opt -> CyclingListControllerBuilder.<Boolean>create(opt)
                                                        .values(Arrays.asList(true, false))
                                                        .formatValue(v -> Component.translatable(v
                                                                        ? "gui.portablestorage.sharing_management.shared"
                                                                        : "gui.portablestorage.sharing_management.forbidden")
                                                                        .withStyle(v ? net.minecraft.ChatFormatting.GREEN
                                                                                        : net.minecraft.ChatFormatting.RED)))
                                        .build());
                }

                return builder.category(categoryBuilder.build())
                                .build()
                                .generateScreen(parent);
        }
}
