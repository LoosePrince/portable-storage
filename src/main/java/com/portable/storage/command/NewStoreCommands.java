package com.portable.storage.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import com.portable.storage.player.PlayerStorageService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;

public final class NewStoreCommands {
    private NewStoreCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(NewStoreCommands::registerInternal);
    }

    private static void registerInternal(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("portable-storage")
            .requires(src -> src.hasPermissionLevel(2))
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
            );
        dispatcher.register(root);
    }

    private static int executeRebuild(CommandContext<ServerCommandSource> ctx, boolean cleanup) {
        MinecraftServer server = ctx.getSource().getServer();
        com.portable.storage.newstore.RefCountRebuilder.rebuild(server, cleanup);
        ctx.getSource().sendFeedback(() -> (cleanup
            ? Text.translatable("command.portable-storage.newstore.rebuild_cleanup")
            : Text.translatable("command.portable-storage.newstore.rebuild")
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
        Map<String, com.portable.storage.newstore.PlayerStore.Entry> m = com.portable.storage.newstore.PlayerStore.readAll(server, player.getUuid());
        long totalLocal = m.values().stream().mapToLong(e -> Math.max(0L, e.count)).sum();
        final int size = m.size();
        final long total = totalLocal;
        ctx.getSource().sendFeedback(() -> Text.translatable("command.portable-storage.newstore.dump", size, total), false);
        return 1;
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
        ctx.getSource().sendFeedback(() -> Text.translatable("command.portable-storage.newstore.migrate", finalMigrated), true);
        return migrated > 0 ? 1 : 0;
    }

    public static boolean migrateOne(MinecraftServer server, java.util.UUID uuid) {
        try {
            // 准备索引
            com.portable.storage.newstore.TemplateIndex index = com.portable.storage.newstore.TemplateIndex.load(server);

            // 在线/离线两路：获取旧版仓库
            net.minecraft.server.network.ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
            com.portable.storage.storage.StorageInventory legacy;
            boolean onlinePlayer = (online != null);
            if (onlinePlayer) {
                legacy = PlayerStorageService.getInventory(online);
            } else {
                legacy = com.portable.storage.player.StoragePersistence.loadStorage(server, uuid);
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
                String key = com.portable.storage.newstore.ItemKeyHasher.hash(st, onlinePlayer ? online.getRegistryManager() : null);
                if (key == null || key.isEmpty()) continue;
                if (index.find(key) == null) {
                    com.portable.storage.newstore.TemplateSlices.putTemplate(() -> server, index, key, st, onlinePlayer ? online.getRegistryManager() : null);
                }
                index.incRef(key, cnt);
                // 玩家计数
                com.portable.storage.newstore.PlayerStore.add(server, uuid, key, cnt, System.currentTimeMillis());
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
            com.portable.storage.player.StoragePersistence.saveStorage(server, uuid, legacy);
            if (onlinePlayer) {
                // 在线玩家：下发同步
                com.portable.storage.net.ServerNetworkingHandlers.sendSync(online);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}


