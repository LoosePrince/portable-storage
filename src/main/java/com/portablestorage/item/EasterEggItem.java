package com.portablestorage.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EasterEggItem extends Item {
    private static final String API_URL = "https://api.baiwumm.com/api/hitokoto?type=d&format=json";
    private static final String DEFAULT_MESSAGE = "你合成我干什么？";
    private static final String NBT_CONTENT = "content";
    private static final String NBT_FROM = "from";
    private static final String NBT_CREATOR = "creator";
    private static final String NBT_FROM_WHO = "from_who";

    public EasterEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (stack.isEmpty() || player.getAbilities().instabuild) return;

        // 检查是否已经处理过（避免重复触发）
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("processed")) {
            return;
        }

        // 标记为已处理
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
        tag.putBoolean("processed", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        // 先消耗彩蛋物品
        stack.shrink(1);
        
        // 给予玩家书与笔和羽毛
        ItemStack writableBook = new ItemStack(Items.WRITABLE_BOOK, 1);
        ItemStack feather = new ItemStack(Items.FEATHER, 1);

        if (!player.getInventory().add(writableBook)) {
            player.drop(writableBook, false);
        }
        if (!player.getInventory().add(feather)) {
            player.drop(feather, false);
        }

        // 异步请求API，获取数据后显示消息
        ServerPlayer finalPlayer = player;
        CompletableFuture.supplyAsync(() -> fetchHitokoto())
            .thenAccept(result -> {
                // 在主线程执行
                finalPlayer.server.execute(() -> {
                    Component messageComponent;
                    
                    if (result != null && result.content != null && !result.content.isEmpty()) {
                        // 构建悬停信息
                        MutableComponent hoverTextBuilder = Component.literal(result.content).withStyle(ChatFormatting.GRAY);
                        
                        if (result.from != null && !result.from.isEmpty()) {
                            if (result.fromWho != null && !result.fromWho.isEmpty()) {
                                hoverTextBuilder = hoverTextBuilder.append(Component.literal("\n"))
                                        .append(Component.literal("—— " + result.from + " · " + result.fromWho)
                                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                            } else {
                                hoverTextBuilder = hoverTextBuilder.append(Component.literal("\n"))
                                        .append(Component.literal("—— " + result.from)
                                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                            }
                        }
                        
                        if (result.creator != null && !result.creator.isEmpty()) {
                            hoverTextBuilder = hoverTextBuilder.append(Component.literal("\n"))
                                    .append(Component.literal("创建者: " + result.creator)
                                            .withStyle(ChatFormatting.DARK_GRAY));
                        }
                        
                        final Component hoverText = hoverTextBuilder;
                        
                        // 创建带悬停事件的消息
                        messageComponent = Component.literal(result.content)
                                .withStyle(style -> style.withHoverEvent(
                                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)
                                ));
                    } else {
                        // 默认消息，无悬停信息
                        messageComponent = Component.literal(DEFAULT_MESSAGE);
                    }
                    
                    // 显示消息
                    finalPlayer.displayClientMessage(messageComponent, false);
                });
            })
            .exceptionally(ex -> {
                // 请求失败，显示默认消息
                finalPlayer.server.execute(() -> {
                    finalPlayer.displayClientMessage(Component.literal(DEFAULT_MESSAGE), false);
                });
                return null;
            });
    }

    private HitokotoResult fetchHitokoto() {
        try {
            URI uri = URI.create(API_URL);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Minecraft-Mod/PortableStorage");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                return parseHitokoto(response.toString());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private HitokotoResult parseHitokoto(String json) {
        try {
            // 简单的JSON解析（因为结构固定）
            HitokotoResult result = new HitokotoResult();
            
            // 提取content字段
            int contentStart = json.indexOf("\"content\":\"") + 11;
            if (contentStart > 10) {
                int contentEnd = json.indexOf("\"", contentStart);
                if (contentEnd > contentStart) {
                    result.content = json.substring(contentStart, contentEnd);
                    result.content = result.content.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r");
                }
            }

            // 提取from字段
            int fromStart = json.indexOf("\"from\":\"") + 8;
            if (fromStart > 7) {
                int fromEnd = json.indexOf("\"", fromStart);
                if (fromEnd > fromStart) {
                    result.from = json.substring(fromStart, fromEnd);
                }
            }

            // 提取creator字段
            int creatorStart = json.indexOf("\"creator\":\"") + 11;
            if (creatorStart > 10) {
                int creatorEnd = json.indexOf("\"", creatorStart);
                if (creatorEnd > creatorStart) {
                    result.creator = json.substring(creatorStart, creatorEnd);
                }
            }

            // 提取from_who字段（可能为null）
            int fromWhoStart = json.indexOf("\"from_who\":") + 11;
            if (fromWhoStart > 10) {
                String fromWhoValue = json.substring(fromWhoStart).trim();
                if (fromWhoValue.startsWith("\"")) {
                    int fromWhoEnd = json.indexOf("\"", fromWhoStart + 1);
                    if (fromWhoEnd > fromWhoStart) {
                        result.fromWho = json.substring(fromWhoStart + 1, fromWhoEnd);
                    }
                } else if (fromWhoValue.startsWith("null")) {
                    result.fromWho = null;
                }
            }

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("tooltip.portablestorage.easter_egg.desc"));
        
        // 显示详细信息（如果物品有存储的数据）
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(NBT_CONTENT)) {
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal(tag.getString(NBT_CONTENT)).withStyle(ChatFormatting.GRAY));
                
                if (tag.contains(NBT_FROM)) {
                    String from = tag.getString(NBT_FROM);
                    if (tag.contains(NBT_FROM_WHO) && !tag.getString(NBT_FROM_WHO).isEmpty()) {
                        tooltip.add(Component.literal("—— " + from + " · " + tag.getString(NBT_FROM_WHO))
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    } else {
                        tooltip.add(Component.literal("—— " + from)
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    }
                }
                
                if (tag.contains(NBT_CREATOR) && !tag.getString(NBT_CREATOR).isEmpty()) {
                    tooltip.add(Component.literal("创建者: " + tag.getString(NBT_CREATOR))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        
        super.appendHoverText(stack, context, tooltip, type);
    }

    private static class HitokotoResult {
        String content;
        String from;
        String creator;
        String fromWho;
    }
}
