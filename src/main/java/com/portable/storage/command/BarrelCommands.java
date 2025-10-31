package com.portable.storage.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public final class BarrelCommands {
    private BarrelCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(BarrelCommands::registerInternal);
    }

    private static void registerInternal(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("portable-storage")
            .requires(src -> src.hasPermissionLevel(2)) // 需要OP权限等级2
            .then(CommandManager.literal("barrel")
                .then(CommandManager.literal("give")
                    .executes(BarrelCommands::executeGiveSelf)
                )
            );
        dispatcher.register(root);
    }

    private static int executeGiveSelf(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        
        // 创建绑定木桶物品
        ItemStack boundBarrel = createBoundBarrel(player);
        
        // 给予玩家物品
        if (player.getInventory().insertStack(boundBarrel)) {
            source.sendFeedback(() -> Text.translatable("portable-storage.command.barrel.give.success"), true);
            return 1;
        } else {
            source.sendFeedback(() -> Text.translatable("portable-storage.command.barrel.give.inventory_full"), true);
            return 0;
        }
    }

    /**
     * 创建绑定到指定玩家的绑定木桶物品
     */
    private static ItemStack createBoundBarrel(ServerPlayerEntity player) {
        ItemStack barrel = new ItemStack(Items.BARREL);
        
        // 创建自定义数据NBT
        NbtCompound customData = new NbtCompound();
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        
        // 设置绑定信息
        customData.putUuid("ps_owner_uuid", playerUuid);
        customData.putLong("ps_owner_uuid_most", playerUuid.getMostSignificantBits());
        customData.putLong("ps_owner_uuid_least", playerUuid.getLeastSignificantBits());
        customData.putString("ps_owner_name", playerName);
        
        // 设置物品的自定义数据
        barrel.getOrCreateNbt().copyFrom(customData);
        
        // 设置自定义名称
        barrel.setCustomName(Text.translatable("item.portable-storage.bound_barrel", playerName));
        
        return barrel;
    }
}
