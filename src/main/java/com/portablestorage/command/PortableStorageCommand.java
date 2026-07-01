package com.portablestorage.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.component.WarehouseEntry;
import com.portablestorage.logic.StorageWriteAudit;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.upgrade.UpgradeRegistry;
import com.portablestorage.upgrade.UpgradeType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;

/**
 * 便携式存储命令
 * 提供仓库管理相关的命令功能
 */
public class PortableStorageCommand {
    /** 正在执行丢出任务的玩家集合，值为下次执行时的 tick 计数 */
    private static final Map<UUID, Integer> DROP_TASKS = new ConcurrentHashMap<>();
    /** 服务器 tick 计数器 */
    private static int serverTickCounter = 0;

    /**
     * 注册命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("portablestorage")
                // 仅允许 4 级 OP 玩家执行（与前面的服务端权限检查语义一致）
                .requires(source -> {
                    ServerPlayer player = source.getPlayer();
                    if (player == null) {
                        return false;
                    }
                    return source.getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
                })
                .executes(context -> {
                    // 显示帮助信息
                    context.getSource().sendSuccess(() -> Component.literal("Portable Storage 命令帮助:\n" +
                            "/portablestorage count [玩家] - 查询仓库物品数量\n" +
                            "/portablestorage upgrade <升级ID> enable [玩家] - 启用升级槽位\n" +
                            "/portablestorage upgrade <升级ID> disable [玩家] - 禁用升级槽位\n" +
                            "/portablestorage drop [玩家] - 开始持续丢出物品\n" +
                            "/portablestorage drop stop [玩家] - 停止丢出任务\n" +
                            "/portablestorage debug status [玩家] - 查看仓库调试状态\n" +
                            "/portablestorage debug sync [玩家] - 强制同步仓库到客户端\n" +
                            "/portablestorage debug storage [玩家] - 查看存储索引与写审计\n" +
                            "/portablestorage debug schema [玩家] - 查看仓库 schema 版本状态"), false);
                    return 1;
                })
                .then(Commands.literal("count")
                        .executes(context -> countItems(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> countItems(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("upgrade")
                        .then(Commands.argument("upgradeId", StringArgumentType.word())
                                .suggests(SUGGEST_UPGRADE_IDS)
                                .then(Commands.literal("enable")
                                        .executes(context -> toggleUpgrade(
                                                context,
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "upgradeId"),
                                                true))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> toggleUpgrade(
                                                        context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "upgradeId"),
                                                        true))))
                                .then(Commands.literal("disable")
                                        .executes(context -> toggleUpgrade(
                                                context,
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "upgradeId"),
                                                false))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> toggleUpgrade(
                                                        context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "upgradeId"),
                                                        false))))))
                .then(Commands.literal("drop")
                        .executes(context -> startDrop(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> startDrop(context, EntityArgument.getPlayer(context, "player"))))
                        .then(Commands.literal("stop")
                                .executes(context -> stopDrop(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> stopDrop(context,
                                                EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("status")
                                .executes(context -> debugStatus(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> debugStatus(context,
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("sync")
                                .executes(context -> debugSync(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> debugSync(context,
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("storage")
                                .executes(context -> debugStorage(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> debugStorage(context,
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("schema")
                                .executes(context -> debugSchema(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> debugSchema(context,
                                                EntityArgument.getPlayer(context, "player")))))));
    }

    /**
     * 查询仓库物品数量
     */
    private static int countItems(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("command.portablestorage.count.no_player"));
            return 0;
        }

        PlayerWarehouse warehouse = WarehouseService.get(player);
        if (!warehouse.isEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("command.portablestorage.count.not_enabled", player.getScoreboardName()));
            return 0;
        }

        // 统计物品种类和总数量
        final int[] itemTypes = { 0 };
        final long[] totalItems = { 0 };
        final int[] fluidTypes = { 0 };
        final long[] totalFluids = { 0 };

        // 统计物品
        for (WarehouseEntry entry : warehouse.getStorageList()) {
            itemTypes[0]++;
            totalItems[0] += entry.getCount();
        }

        // 统计流体
        for (Map.Entry<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant, Long> entry : warehouse
                .getFluidStorageMap().entrySet()) {
            fluidTypes[0]++;
            totalFluids[0] += entry.getValue();
        }

        // 统计升级数量
        int upgradeCount = warehouse.getUpgradeStorage().size();

        // 发送结果
        final int finalItemTypes = itemTypes[0];
        final long finalTotalItems = totalItems[0];
        final int finalFluidTypes = fluidTypes[0];
        final long finalTotalFluids = totalFluids[0];
        context.getSource().sendSuccess(() -> Component.translatable("command.portablestorage.count.result",
                player.getScoreboardName(),
                finalItemTypes,
                finalTotalItems,
                finalFluidTypes,
                finalTotalFluids / net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET,
                upgradeCount), true);

        return 1;
    }

    /**
     * 启用/禁用升级槽位
     */
    private static int toggleUpgrade(CommandContext<CommandSourceStack> context, ServerPlayer player,
            String upgradeIdStr, boolean enable) {
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("command.portablestorage.upgrade.no_player"));
            return 0;
        }

        // 升级ID只接受简写格式（不包含冒号），自动添加 portablestorage 命名空间
        Identifier upgradeId = com.portablestorage.PortableStorage.id(upgradeIdStr);

        UpgradeType upgradeType = UpgradeRegistry.get(upgradeId);
        if (upgradeType == null) {
            context.getSource()
                    .sendFailure(Component.translatable("command.portablestorage.upgrade.not_found", upgradeIdStr));
            return 0;
        }

        PlayerWarehouse warehouse = WarehouseService.get(player);
        if (!warehouse.isEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("command.portablestorage.upgrade.not_enabled", player.getScoreboardName()));
            return 0;
        }

        if (enable) {
            // 启用：添加升级物品（使用升级类型要求的物品）
            ItemStack upgradeStack = upgradeType.getIconStack();
            if (upgradeStack.isEmpty()) {
                // 如果没有图标堆叠，尝试查找一个有效的物品
                // 使用一些常见物品进行测试
                ItemStack[] testItems = {
                        new ItemStack(net.minecraft.world.item.Items.STICK),
                        new ItemStack(net.minecraft.world.item.Items.DIRT),
                        new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)
                };

                ItemStack foundStack = ItemStack.EMPTY;
                for (ItemStack testStack : testItems) {
                    if (upgradeType.isItemValid(testStack)) {
                        foundStack = testStack;
                        break;
                    }
                }

                // 如果常见物品都不符合，尝试遍历注册表（可能较慢）
                if (foundStack.isEmpty()) {
                    for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                        ItemStack testStack = new ItemStack(item);
                        if (upgradeType.isItemValid(testStack)) {
                            foundStack = testStack;
                            break;
                        }
                    }
                }

                if (foundStack.isEmpty()) {
                    context.getSource().sendFailure(
                            Component.translatable("command.portablestorage.upgrade.no_item", upgradeIdStr));
                    return 0;
                }
                upgradeStack = foundStack;
            }
            ItemStack finalUpgradeStack = upgradeStack;
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "command_upgrade.enable", () -> {
                warehouse.setUpgrade(upgradeId, finalUpgradeStack.copyWithCount(1));
                return null;
            });
            context.getSource().sendSuccess(() -> Component.translatable("command.portablestorage.upgrade.enabled",
                    upgradeIdStr, player.getScoreboardName()), true);
        } else {
            // 禁用：移除升级物品
            ItemStack current = warehouse.getUpgrade(upgradeId);
            if (current.isEmpty()) {
                context.getSource().sendFailure(
                        Component.translatable("command.portablestorage.upgrade.already_disabled", upgradeIdStr));
                return 0;
            }
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "command_upgrade.disable", () -> {
                warehouse.setUpgrade(upgradeId, ItemStack.EMPTY);
                return null;
            });
            context.getSource().sendSuccess(() -> Component.translatable("command.portablestorage.upgrade.disabled",
                    upgradeIdStr, player.getScoreboardName()), true);
        }

        return 1;
    }

    /**
     * 开始持续丢出物品
     */
    private static int startDrop(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("command.portablestorage.drop.no_player"));
            return 0;
        }

        UUID playerUuid = player.getUUID();
        if (DROP_TASKS.containsKey(playerUuid)) {
            context.getSource().sendFailure(
                    Component.translatable("command.portablestorage.drop.already_running", player.getScoreboardName()));
            return 0;
        }

        PlayerWarehouse warehouse = WarehouseService.get(player);
        if (!warehouse.isEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("command.portablestorage.drop.not_enabled", player.getScoreboardName()));
            return 0;
        }

        // 标记任务开始（立即执行一次，然后每 4 tick 执行一次，即 0.2 秒）
        // 使用 -1 表示立即执行
        DROP_TASKS.put(playerUuid, -1);
        context.getSource().sendSuccess(
                () -> Component.translatable("command.portablestorage.drop.started", player.getScoreboardName()), true);

        return 1;
    }

    /**
     * 停止持续丢出物品
     */
    private static int stopDrop(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("command.portablestorage.drop.no_player"));
            return 0;
        }

        UUID playerUuid = player.getUUID();
        if (!DROP_TASKS.containsKey(playerUuid)) {
            context.getSource().sendFailure(
                    Component.translatable("command.portablestorage.drop.not_running", player.getScoreboardName()));
            return 0;
        }

        DROP_TASKS.remove(playerUuid);
        context.getSource().sendSuccess(
                () -> Component.translatable("command.portablestorage.drop.stopped", player.getScoreboardName()), true);

        return 1;
    }

    private static int debugStatus(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (player == null) {
            context.getSource().sendFailure(Component.literal("无法获取目标玩家"));
            return 0;
        }

        PlayerWarehouse warehouse = WarehouseService.get(player);
        String upgrades = warehouse.getUpgradeStorage().keySet().stream()
                .map(Identifier::toString)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");

        context.getSource().sendSuccess(() -> Component.literal(
                "PortableStorage Debug Status\n" +
                        "player=" + player.getScoreboardName() + "\n" +
                        "enabled=" + warehouse.isEnabled() + "\n" +
                        "type=" + warehouse.getType() + "\n" +
                        "effectiveType=" + warehouse.getEffectiveType() + "\n" +
                        "folded=" + warehouse.isFolded() + "\n" +
                        "visibleRows=" + warehouse.getVisibleRows() + "\n" +
                        "quickInteraction=" + warehouse.isQuickInteraction() + "\n" +
                        "smartCollapse=" + warehouse.isSmartCollapse() + "\n" +
                        "craftRefill=" + warehouse.isCraftRefill() + "\n" +
                        "workbenchUpgrade=" + warehouse.hasWorkbenchUpgrade() + "\n" +
                        "upgradeCount=" + warehouse.getUpgradeStorage().size() + "\n" +
                        "upgrades=" + upgrades), false);
        return 1;
    }

    private static int debugSync(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (player == null) {
            context.getSource().sendFailure(Component.literal("无法获取目标玩家"));
            return 0;
        }

        WarehouseService.sync(player);
        context.getSource().sendSuccess(
                () -> Component.literal("已请求同步仓库到客户端: " + player.getScoreboardName()),
                true);
        return 1;
    }

    private static int debugStorage(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (player == null) {
            context.getSource().sendFailure(Component.literal("无法获取目标玩家"));
            return 0;
        }
        PlayerWarehouse warehouse = WarehouseService.get(player);
        Map<String, Integer> bucketStats = warehouse.getTypeBucketStats();
        String buckets = bucketStats.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");

        context.getSource().sendSuccess(() -> Component.literal(
                "PortableStorage Debug Storage\n" +
                        "player=" + player.getScoreboardName() + "\n" +
                        "storageRevision=" + warehouse.getStorageRevision() + "\n" +
                        "dirtyCount=" + warehouse.getDirtyCount() + "\n" +
                        "logicalSlotCount=" + warehouse.getLogicalSlotCount() + "\n" +
                        "typeBuckets=" + buckets + "\n" +
                        "auditTotalWrites=" + StorageWriteAudit.getTotalWrites() + "\n" +
                        "auditLastSource=" + StorageWriteAudit.getLastSource() + "\n" +
                        "auditLastDecision=" + StorageWriteAudit.getLastDecision() + "\n" +
                        "auditBySource=" + StorageWriteAudit.snapshotBySource()),
                false);
        return 1;
    }

    private static int debugSchema(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        if (player == null) {
            context.getSource().sendFailure(Component.literal("无法获取目标玩家"));
            return 0;
        }
        PlayerWarehouse warehouse = WarehouseService.get(player);
        context.getSource().sendSuccess(() -> Component.literal(
                "PortableStorage Debug Schema\n" +
                        "player=" + player.getScoreboardName() + "\n" +
                        "loadedSchemaVersion=" + warehouse.getLoadedSchemaVersion() + "\n" +
                        "loadedFromUnifiedStorage=" + warehouse.isLoadedFromUnifiedStorage() + "\n" +
                        "targetSchemaVersion=" + warehouse.getTargetSchemaVersion() + "\n" +
                        "note=migration_runs_on_load_and_persists_on_save"),
                false);
        return 1;
    }

    /**
     * 处理丢出任务（在服务器 tick 中调用）
     * 这个方法应该在 ServerTickEvents.END_SERVER_TICK 中调用
     */
    public static void tickDropTasks(net.minecraft.server.MinecraftServer server) {
        serverTickCounter++;

        // 遍历所有任务
        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(DROP_TASKS.entrySet())) {
            UUID playerUuid = entry.getKey();
            int targetTick = entry.getValue();

            // 检查是否到了执行时间（-1 表示立即执行）
            if (targetTick == -1 || serverTickCounter >= targetTick) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                if (player == null || !player.isAlive() || player.hasDisconnected()) {
                    DROP_TASKS.remove(playerUuid);
                    continue;
                }

                PlayerWarehouse warehouse = WarehouseService.get(player);
                if (!warehouse.isEnabled()) {
                    DROP_TASKS.remove(playerUuid);
                    continue;
                }

                // 检查仓库是否为空
                if (warehouse.isEmpty()) {
                    DROP_TASKS.remove(playerUuid);
                    player.sendSystemMessage(Component.translatable("command.portablestorage.drop.completed"));
                    continue;
                }

                // 获取排序后的条目列表
                List<WarehouseEntry> entries = warehouse.getSortedEntries();
                if (entries.isEmpty()) {
                    DROP_TASKS.remove(playerUuid);
                    player.sendSystemMessage(Component.translatable("command.portablestorage.drop.completed"));
                    continue;
                }

                // 查找第一个可丢出的物品（跳过虚拟物品和折叠项）
                WarehouseEntry targetEntry = null;
                int targetIndex = 0;
                for (int i = 0; i < entries.size(); i++) {
                    WarehouseEntry entry2 = entries.get(i);
                    ItemStack itemStack = entry2.getItemStack();

                    // 跳过虚拟物品（流体、经验等）
                    if (itemStack.is(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE) ||
                            itemStack.is(com.portablestorage.item.ModItems.VIRTUAL_LAVA) ||
                            itemStack.is(com.portablestorage.item.ModItems.VIRTUAL_WATER) ||
                            itemStack.is(com.portablestorage.item.ModItems.VIRTUAL_MILK)) {
                        continue;
                    }

                    // 检查是否为折叠项
                    net.minecraft.world.item.component.CustomData customData = itemStack
                            .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    boolean isCollapsed = customData != null && customData.copyTag()
                            .getBoolean(com.portablestorage.util.WarehouseConstants.SMART_COLLAPSE_TAG)
                            .orElse(false);
                    if (isCollapsed) {
                        continue;
                    }

                    // 找到可丢出的物品
                    targetEntry = entry2;
                    targetIndex = i;
                    break;
                }

                if (targetEntry == null) {
                    // 没有可丢出的物品，4 tick 后重试
                    DROP_TASKS.put(playerUuid, serverTickCounter + 4);
                    continue;
                }

                // 计算要丢出的数量（1组）
                int maxStackSize = targetEntry.getItemStack().getMaxStackSize();
                long availableCount = warehouse.getRealCount(targetIndex); // 获取该槽位的实际数量
                int toDrop = (int) Math.min(maxStackSize, Math.min(availableCount, Integer.MAX_VALUE));

                if (toDrop > 0) {
                    final int dropSlotIndex = targetIndex;
                    final int dropAmount = toDrop;
                    ItemStack dropped = WarehouseService.commitIfWarehouseChanged(player, warehouse,
                            "command_drop_task", () -> WarehouseManager.removeItem(warehouse, dropSlotIndex, dropAmount, true));
                    if (!dropped.isEmpty()) {
                        // 丢出物品
                        player.drop(dropped, true);
                    }
                }

                // 调度下一次丢出（4 tick 后，即 0.2 秒）
                DROP_TASKS.put(playerUuid, serverTickCounter + 4);
            }
        }
    }

    /**
     * 升级ID建议提供器
     * 只提供简写格式（不包含命名空间）
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_UPGRADE_IDS = (context, builder) -> {
        List<UpgradeType> upgrades = UpgradeRegistry.getAllUpgrades();
        for (UpgradeType upgrade : upgrades) {
            // 只提供简写格式（路径部分）
            String shortId = upgrade.getId().getPath();
            builder.suggest(shortId);
        }
        return builder.buildFuture();
    };
}
