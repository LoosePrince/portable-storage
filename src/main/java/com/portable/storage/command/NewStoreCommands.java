package com.portable.storage.command;

import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.portable.storage.PortableStorage;
import com.portable.storage.net.ServerNetworkingHandlers;
import com.portable.storage.newstore.ItemKeyHasher;
import com.portable.storage.newstore.PlayerStore;
import com.portable.storage.newstore.RefCountRebuilder;
import com.portable.storage.newstore.StoragePaths;
import com.portable.storage.newstore.TemplateIndex;
import com.portable.storage.newstore.TemplateSlices;
import com.portable.storage.player.PlayerStorageService;
import com.portable.storage.player.StoragePersistence;
import com.portable.storage.storage.StorageInventory;
import com.portable.storage.util.SafeNbtIo;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class NewStoreCommands {
    private NewStoreCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(NewStoreCommands::registerInternal);
    }

    private static void registerInternal(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("portable-storage")
            .requires(src -> src.hasPermissionLevel(4))
            .then(CommandManager.literal("newstore")
                .then(CommandManager.literal("migrate")
                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                        .executes(NewStoreCommands::executeMigrate)
                    )
                )
                .then(CommandManager.literal("rebuild")
                    .executes(ctx -> executeRebuild(ctx, false))
                    .then(CommandManager.argument("cleanup", BoolArgumentType.bool())
                        .executes(ctx -> executeRebuild(ctx, BoolArgumentType.getBool(ctx, "cleanup")))
                    )
                )
                .then(CommandManager.literal("dump")
                    .executes(NewStoreCommands::executeDumpSelf)
                )
                .then(CommandManager.literal("verify")
                    .executes(NewStoreCommands::executeVerify)
                )
                .then(CommandManager.literal("scan-corrupt")
                    .executes(NewStoreCommands::executeScanCorrupt)
                )
                .then(CommandManager.literal("repair")
                    .executes(NewStoreCommands::executeRepair)
                )
                .then(CommandManager.literal("list")
                    .executes(NewStoreCommands::executeList)
                )
                .then(CommandManager.literal("list-keys")
                    .executes(NewStoreCommands::executeListKeys)
                )
                .then(CommandManager.literal("inspect-unknown")
                    .executes(NewStoreCommands::executeInspectUnknown)
                )
                .then(CommandManager.literal("test-large-item")
                    .executes(NewStoreCommands::executeTestLargeItem)
                )
                .then(CommandManager.literal("get-item-size")
                    .executes(NewStoreCommands::executeGetItemSize)
                )
            );
        dispatcher.register(root);
    }

    private static int executeRebuild(CommandContext<ServerCommandSource> ctx, boolean cleanup) {
        MinecraftServer server = ctx.getSource().getServer();
        RefCountRebuilder.rebuild(server, cleanup);
        ctx.getSource().sendFeedback(() -> (cleanup
            ? Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.rebuild_cleanup")
            : Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.rebuild")
        ), true);
        return 1;
    }

    private static int executeDumpSelf(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can run dump without args"));
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        Map<String, PlayerStore.Entry> m = PlayerStore.readAll(server, player.getUuid());
        // 修复：直接使用 count 值，因为 readAll 已经过滤了 count <= 0 的条目
        long totalLocal = m.values().stream().mapToLong(e -> e.count).sum();
        final int size = m.size();
        final long total = totalLocal;
        
        // 添加调试信息：检查是否有异常数据
        long negativeCount = m.values().stream().mapToLong(e -> e.count < 0 ? e.count : 0).sum();
        long zeroCount = m.values().stream().mapToLong(e -> e.count == 0 ? 1 : 0).sum();
        
        if (negativeCount != 0 || zeroCount > 0) {
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.dump.warning", negativeCount, zeroCount), false);
        }
        
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.dump", size, total), false);
        return 1;
    }

    private static int executeVerify(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can run verify without args"));
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        
        Map<String, PlayerStore.Entry> m = PlayerStore.readAll(server, player.getUuid());
        int totalEntries = m.size();
        long totalCount = m.values().stream().mapToLong(e -> e.count).sum();
        
        // 检查数据一致性
        final int[] negativeEntries = {0};
        final int[] zeroEntries = {0};
        final int[] invalidEntries = {0};
        
        for (PlayerStore.Entry entry : m.values()) {
            if (entry.count < 0) {
                negativeEntries[0]++;
            } else if (entry.count == 0) {
                zeroEntries[0]++;
            }
            if (entry.key == null || entry.key.isEmpty()) {
                invalidEntries[0]++;
            }
        }
        
        // 输出验证结果
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.title"), false);
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.total_entries", totalEntries), false);
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.total_count", totalCount), false);
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.negative_entries", negativeEntries[0]), false);
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.zero_entries", zeroEntries[0]), false);
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.invalid_entries", invalidEntries[0]), false);
        
        if (negativeEntries[0] > 0 || zeroEntries[0] > 0 || invalidEntries[0] > 0) {
            ctx.getSource().sendError(Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.inconsistent"));
            return 0;
        } else {
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.verify.passed"), false);
            return 1;
        }
    }

    private static int executeMigrate(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        java.util.Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "player");
        int migrated = 0;
        for (var gp : profiles) {
            java.util.UUID uuid = gp.getId();
            if (uuid == null) continue;
            boolean ok = migrateOne(server, uuid);
            if (ok) migrated++;
        }
        final int finalMigrated = migrated;
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.migrate", finalMigrated), true);
        return migrated > 0 ? 1 : 0;
    }

    public static boolean migrateOne(MinecraftServer server, java.util.UUID uuid) {
        try {
            // 准备索引
            TemplateIndex index = TemplateIndex.load(server);

            // 在线/离线两路：获取旧版仓库
            net.minecraft.server.network.ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
            StorageInventory legacy;
            boolean onlinePlayer = (online != null);
            if (onlinePlayer) {
                legacy = PlayerStorageService.getInventory(online);
            } else {
                legacy = StoragePersistence.loadStorage(server, uuid);
            }

            // 汇总所有条目
            java.util.ArrayList<net.minecraft.item.ItemStack> stacks = new java.util.ArrayList<>();
            java.util.ArrayList<Long> counts = new java.util.ArrayList<>();
            java.util.ArrayList<Long> timestamps = new java.util.ArrayList<>();
            for (int i = 0; i < legacy.getCapacity(); i++) {
                net.minecraft.item.ItemStack disp = legacy.getDisplayStack(i);
                long cnt = legacy.getCountByIndex(i);
                if (disp.isEmpty() || cnt <= 0) continue;
                stacks.add(disp.copy());
                counts.add(cnt);
                timestamps.add(legacy.getTimestampByIndex(i));
            }

        // 若无旧数据，则不迁移（避免每次登录提示）
        if (stacks.isEmpty()) {
            return false;
        }

        // 将旧版全部迁移到新版（模板+玩家计数），并清空旧版（take 掉）
            for (int i = 0; i < stacks.size(); i++) {
                net.minecraft.item.ItemStack st = stacks.get(i);
                long cnt = counts.get(i);
                // 模板与索引
                String key = ItemKeyHasher.hash(st, onlinePlayer ? online.getRegistryManager() : null);
                if (key == null || key.isEmpty()) continue;
                if (index.find(key) == null) {
                    TemplateSlices.putTemplate(() -> server, index, key, st, onlinePlayer ? online.getRegistryManager() : null);
                }
                index.incRef(key, cnt);
                // 玩家计数
                PlayerStore.add(server, uuid, key, cnt, System.currentTimeMillis());
            }

            // 清空旧版（逐条 take 掉）
            for (int i = 0; i < legacy.getCapacity(); i++) {
                long left = legacy.getCountByIndex(i);
                if (left > 0) legacy.takeByIndex(i, left, System.currentTimeMillis());
            }
            
            // 强制清空所有变体，确保没有残留
            legacy.clear();

            // 持久化：索引、新版玩家文件已在 add 内写；写回玩家 .dat 清空旧版（无论在线/离线）
            index.save(server);
            StoragePersistence.saveStorage(server, uuid, legacy);
            if (onlinePlayer) {
                // 在线玩家：下发同步
                ServerNetworkingHandlers.sendSync(online);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    
    private static int executeScanCorrupt(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        final int[] corruptCount = {0};
        
        // 扫描模板切片目录
        Path templatesDir = StoragePaths.getTemplatesDir(server);
        corruptCount[0] += SafeNbtIo.scanForCorruptFiles(templatesDir);
        
        // 扫描玩家数据目录
        Path playersDir = StoragePaths.getPlayersDir(server);
        corruptCount[0] += SafeNbtIo.scanForCorruptFiles(playersDir);
        
        if (corruptCount[0] > 0) {
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.scan_corrupt.found", corruptCount[0]), true);
        } else {
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.scan_corrupt.none"), true);
        }
        
        return corruptCount[0];
    }
    
    private static int executeRepair(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        final int[] repairedCount = {0};
        
        // 尝试从备份恢复损坏的模板文件
        Path templatesDir = StoragePaths.getTemplatesDir(server);
        if (Files.exists(templatesDir)) {
            try (var stream = Files.list(templatesDir)) {
                for (Path file : stream.toList()) {
                    if (file.getFileName().toString().endsWith(".corrupt")) {
                        Path originalFile = file.resolveSibling(file.getFileName().toString().replace(".corrupt", ""));
                        Path backupFile = originalFile.resolveSibling(originalFile.getFileName() + ".bak");
                        
                        if (Files.exists(backupFile)) {
                            try {
                                // 验证备份文件
                                net.minecraft.nbt.NbtIo.readCompressed(backupFile, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
                                // 恢复文件
                                Files.move(backupFile, originalFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                Files.deleteIfExists(file);
                                SafeNbtIo.clearCorruptMark(originalFile);
                                repairedCount[0]++;
                                PortableStorage.LOGGER.info("从备份恢复文件: {} -> {}", backupFile, originalFile);
                            } catch (Exception e) {
                                PortableStorage.LOGGER.warn("无法从备份恢复文件: {}", backupFile, e);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                PortableStorage.LOGGER.error("扫描修复文件时出错", e);
            }
        }
        
        if (repairedCount[0] > 0) {
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.repair.success", repairedCount[0]), true);
        } else {
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.repair.none"), true);
        }
        
        return repairedCount[0];
    }

    private static Text trOrLiteral(String key, Object... args) {
        if (Language.getInstance().hasTranslation(key)) {
            return Text.translatable(key, args);
        }
        // 简单拼接为可读字符串作为兜底
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(key).append("] ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.valueOf(args[i]));
        }
        return Text.literal(sb.toString());
    }

    private static int executeList(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can run this command"));
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        Map<String, PlayerStore.Entry> entries = PlayerStore.readAll(server, player.getUuid());
        int totalEntries = entries.size();
        long totalCount = entries.values().stream().mapToLong(e -> e.count).sum();

        ctx.getSource().sendFeedback(() -> trOrLiteral("[Player Store] Total Entries: " + totalEntries + ", Total Count: " + totalCount), false);

        var index = com.portable.storage.newstore.StorageMemoryCache.getTemplateIndex();
        var cache = com.portable.storage.newstore.StorageMemoryCache.getTemplateCache();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (PlayerStore.Entry e : entries.values()) {
            net.minecraft.item.ItemStack stack = cache.get(e.key);
            if (stack == null || stack.isEmpty()) {
                stack = com.portable.storage.newstore.TemplateSlices.getTemplate(() -> server, index, e.key, server.getRegistryManager());
                if (stack != null && !stack.isEmpty()) {
                    com.portable.storage.newstore.StorageMemoryCache.addTemplate(e.key, stack);
                }
            }
            String name = (stack != null && !stack.isEmpty()) ? stack.getName().getString() : "unknown";
            String tsStr = Instant.ofEpochMilli(e.ts).atZone(ZoneId.systemDefault()).format(fmt);
            ctx.getSource().sendFeedback(() -> trOrLiteral("[Player Store] Item: " + name + ", Key: " + e.key + ", Count: " + e.count + ", Timestamp: " + tsStr), false);
        }
        return 1;
    }

    /**
     * 扫描玩家条目中在 list 中为 unknown 的键，并直接从磁盘切片读取详细信息。
     * 输出：key、所在 slice、模板是否存在、反序列化是否成功、物品名称/ID、计数与时间戳。
     */
    private static int executeInspectUnknown(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can run this command"));
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        Map<String, PlayerStore.Entry> entries = PlayerStore.readAll(server, player.getUuid());
        TemplateIndex index = com.portable.storage.newstore.StorageMemoryCache.getTemplateIndex();
        java.util.Map<String, net.minecraft.item.ItemStack> cache = com.portable.storage.newstore.StorageMemoryCache.getTemplateCache();

        int inspected = 0;
        int unknownCount = 0;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (PlayerStore.Entry e : entries.values()) {
            if (e.count <= 0) continue;
            net.minecraft.item.ItemStack cached = cache.get(e.key);
            boolean isUnknown = (cached == null || cached.isEmpty());
            if (!isUnknown) continue;

            unknownCount++;
            TemplateIndex.Entry ie = index.find(e.key);
            int slice = (ie != null) ? ie.slice : -1;

            // 直接从文件读取，不依赖缓存
            net.minecraft.item.ItemStack fromDisk = TemplateSlices.getTemplate(() -> server, index, e.key, server.getRegistryManager());
            boolean existsOnDisk = !fromDisk.isEmpty();
            String itemName = existsOnDisk ? fromDisk.getName().getString() : "<parse-failed>";
            String tsStr = java.time.Instant.ofEpochMilli(e.ts).atZone(ZoneId.systemDefault()).format(fmt);

            ctx.getSource().sendFeedback(() -> trOrLiteral(
                "[Inspect Unknown] key=" + e.key
                + ", slice=" + slice
                + ", onDisk=" + existsOnDisk
                + ", name=" + itemName
                + ", count=" + e.count
                + ", ts=" + tsStr
            ), false);
            inspected++;
        }

        if (inspected == 0) {
            if (unknownCount == 0) {
                ctx.getSource().sendFeedback(() -> trOrLiteral("[Inspect Unknown] none"), false);
            } else {
                ctx.getSource().sendFeedback(() -> trOrLiteral("[Inspect Unknown] none-parsed"), false);
            }
        }
        return inspected;
    }

    private static int executeListKeys(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can run this command"));
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        Map<String, PlayerStore.Entry> entries = PlayerStore.readAll(server, player.getUuid());
        int totalEntries = entries.size();
        long totalCount = entries.values().stream().mapToLong(e -> e.count).sum();

        ctx.getSource().sendFeedback(() -> trOrLiteral("[Player Store] Total Entries: " + totalEntries + ", Total Count: " + totalCount), false);
        for (PlayerStore.Entry e : entries.values()) {
            ctx.getSource().sendFeedback(() -> trOrLiteral("[Player Store] Key: " + e.key + ", Count: " + e.count), false);
        }
        return 1;
    }
    
    private static int executeTestLargeItem(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can run this command"));
            return 0;
        }
        
        // 创建一个带有大量NBT数据的物品
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.WRITTEN_BOOK);
        
        // 创建大量页面来增加物品大小，每页包含不重复的内容
        net.minecraft.nbt.NbtList pages = new net.minecraft.nbt.NbtList();
        
        // 创建200页，每页包含不重复的文本
        // 反正也打不开，随便搞，单个大小约 146kb 注意别给整成禁人书了，使用此命令概不负责
        for (int pageNum = 0; pageNum < 200; pageNum++) {
            StringBuilder pageContent = new StringBuilder();
            pageContent.append("测试页面 ").append(pageNum + 1).append("\n");
            pageContent.append("这是用于测试存储大小限制的页面。\n");
            pageContent.append("每页包含独特的内容来增加数据大小。\n\n");
            
            // 每页添加独特的内容
            for (int i = 0; i < 20; i++) {
                int lineNum = pageNum * 20 + i + 1;
                pageContent.append("独特内容行 ").append(lineNum).append(": ");
                
                // 生成每行独特的内容
                pageContent.append("这是第").append(lineNum).append("行的独特文本内容。");
                pageContent.append("包含随机数据: ").append(System.currentTimeMillis() + lineNum).append("。");
                pageContent.append("用于测试物品大小限制功能。");
                pageContent.append("行号: ").append(lineNum).append("，页面: ").append(pageNum + 1).append("。");
                pageContent.append("时间戳: ").append(System.nanoTime() + lineNum).append("。");
                pageContent.append("随机字符串: ").append(generateRandomString(20)).append("。\n");
            }
            
            // 添加页面特有的信息
            pageContent.append("\n页面特有信息:\n");
            pageContent.append("- 页面编号: ").append(pageNum + 1).append("\n");
            pageContent.append("- 生成时间: ").append(System.currentTimeMillis()).append("\n");
            pageContent.append("- 页面哈希: ").append(Integer.toHexString((pageNum + 1) * 12345)).append("\n");
            pageContent.append("- 内容长度: ").append(pageContent.length()).append(" 字符\n");
            
            // 创建页面文本，使用JSON格式
            String pageText = "{\"text\":\"" + pageContent.toString().replace("\"", "\\\"") + "\"}";
            pages.add(net.minecraft.nbt.NbtString.of(pageText));
        }
        
        // 设置物品的NBT数据
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putString("title", "大型测试物品");
        nbt.putString("author", "Portable Storage");
        nbt.put("pages", pages);
        
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.of(nbt));
        
        // 给玩家物品
        if (player.getInventory().insertStack(stack)) {
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.test_large_item.success"), true);
        } else {
            // 如果背包满了，丢到地上
            player.dropItem(stack, false);
            ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.test_large_item.dropped"), true);
        }
        
        return 1;
    }
    
    private static int executeGetItemSize(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can run this command"));
            return 0;
        }
        
        net.minecraft.item.ItemStack mainHand = player.getMainHandStack();
        if (mainHand.isEmpty()) {
            ctx.getSource().sendError(Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.get_item_size.no_item"));
            return 0;
        }
        
        // 计算物品大小
        long itemSize = calculateItemSize(mainHand, player.getRegistryManager());
        String formattedSize = formatSize(itemSize);
        
        // 获取物品信息
        String itemName = mainHand.getName().getString();
        int count = mainHand.getCount();
        
        // 发送结果
        ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.get_item_size.result", 
            itemName, count, formattedSize), false);
        
        // 检查是否超过限制
        com.portable.storage.config.ServerConfig config = com.portable.storage.config.ServerConfig.getInstance();
        if (config.isEnableSizeLimit()) {
            long maxSize = config.getMaxStorageSizeBytes();
            if (itemSize > maxSize) {
                ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.get_item_size.exceeds_limit", 
                    formatSize(maxSize)), false);
            } else {
                ctx.getSource().sendFeedback(() -> Text.translatable("command." + PortableStorage.MOD_ID + ".newstore.get_item_size.within_limit", 
                    formatSize(maxSize)), false);
            }
        }
        
        return 1;
    }
    
    /**
     * 计算单个物品的大小
     */
    private static long calculateItemSize(net.minecraft.item.ItemStack stack, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        if (stack == null || stack.isEmpty()) return 0;
        
        try {
            // 将物品序列化为NBT
            net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
            var encoded = net.minecraft.item.ItemStack.CODEC.encodeStart(
                net.minecraft.registry.RegistryOps.of(net.minecraft.nbt.NbtOps.INSTANCE, lookup), 
                stack
            );
            
            if (encoded.result().isEmpty()) return 0;
            
            net.minecraft.nbt.NbtElement itemNbt = encoded.result().get();
            nbt.put("item", itemNbt);
            nbt.putInt("count", stack.getCount());
            
            // 计算NBT大小
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(nbt, baos);
            return baos.size();
        } catch (Exception e) {
            // 估算大小
            return 64 + (stack.getComponents().isEmpty() ? 0 : 100);
        }
    }
    
    /**
     * 格式化字节大小
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * 生成指定长度的随机字符串
     */
    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
}


