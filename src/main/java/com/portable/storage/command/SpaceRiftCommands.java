package com.portable.storage.command;

import java.util.Collection;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.portable.storage.world.SpaceRiftManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class SpaceRiftCommands {
    private SpaceRiftCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(SpaceRiftCommands::registerInternal);
    }

    private static void registerInternal(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("portable-storage")
            .requires(src -> src.hasPermissionLevel(4))
            .then(CommandManager.literal("rift")
                .then(CommandManager.literal("reset")
                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                        .executes(SpaceRiftCommands::executeReset)
                    )
                )
            );
        dispatcher.register(root);
    }

    private static int executeReset(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Collection<com.mojang.authlib.GameProfile> profiles = GameProfileArgumentType.getProfileArgument(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();
        ServerWorld rift = SpaceRiftManager.getWorld(server);
        if (rift == null) {
            ctx.getSource().sendError(Text.literal("Space Rift dimension not found"));
            return 0;
        }
        for (com.mojang.authlib.GameProfile profile : profiles) {
            UUID id = profile.getId();
            if (id == null) continue;
            ChunkPos origin = SpaceRiftManager.ensureAllocatedPlot(server, id);
            // 清空 16x16 x 164 的区域
            int minX = origin.getStartX();
            int minZ = origin.getStartZ();
            int maxX = minX + 16 - 1;
            int maxZ = minZ + 16 - 1;
            for (int y = 0; y <= 164; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        rift.setBlockState(new BlockPos(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState());
                    }
                }
            }
            // 重新生成平台
            SpaceRiftManager.ensurePlotInitialized(rift, origin, id, true);
            // 若在线，重设边界并拉回中心
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(profile.getId());
            if (target != null) {
                BlockPos center = SpaceRiftManager.getPlotCenterBlock(origin);
                target.teleport(rift, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, target.getYaw(), target.getPitch());
                SpaceRiftManager.applyPersonalBorder(target);
            }
            ctx.getSource().sendFeedback(() -> Text.literal("Reset space rift plot for " + profile.getName()), true);
        }
        return 1;
    }
}


