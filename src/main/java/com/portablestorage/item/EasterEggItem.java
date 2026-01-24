package com.portablestorage.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EasterEggItem extends Item {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/LoosePrince/portable-storage/releases/tags/note";
    private static final String HITOKOTO_API_URL = "https://api.baiwumm.com/api/hitokoto?type=d&format=json";
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
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("processed")) {
            return;
        }

        // 标记为已处理
        if (tag == null) {
            tag = new CompoundTag();
        }
        tag.putBoolean("processed", true);
        stack.setTag(tag);

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
        EasterEggItem instance = this;
        CompletableFuture.supplyAsync(() -> {
            // 首先尝试从 GitHub 获取 release 内容
            String githubContent = instance.fetchGitHubRelease();
            if (githubContent != null && !githubContent.trim().isEmpty()) {
                return new MessageResult(githubContent, null, null, null, true);
            }
            // 如果 GitHub 没有内容，则获取一言
            HitokotoResult hitokoto = instance.fetchHitokoto();
            if (hitokoto != null && hitokoto.content != null && !hitokoto.content.isEmpty()) {
                return new MessageResult(hitokoto.content, hitokoto.from, hitokoto.creator, hitokoto.fromWho, false);
            }
            return null;
        })
            .thenAccept(result -> {
                // 在主线程执行
                finalPlayer.server.execute(() -> {
                    Component messageComponent;
                    
                    if (result != null && result.content != null && !result.content.isEmpty()) {
                        if (result.isGitHubContent) {
                            // GitHub 内容，直接显示
                            messageComponent = Component.literal(result.content).withStyle(ChatFormatting.GOLD);
                        } else {
                            // 一言内容，构建悬停信息
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
                        }
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

    /**
     * 从 GitHub API 获取 release tag "note" 的内容
     */
    private String fetchGitHubRelease() {
        try {
            URI uri = URI.create(GITHUB_API_URL);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Minecraft-Mod/PortableStorage");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // 404 表示该 tag 不存在或没有内容，返回 null
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                return parseGitHubRelease(response.toString());
            }
        } catch (Exception e) {
            // 请求失败，返回 null，将使用一言
            return null;
        }
    }

    /**
     * 解析 GitHub Release JSON，提取 body 字段
     */
    private String parseGitHubRelease(String json) {
        try {
            // 查找 "body" 字段
            int bodyStart = json.indexOf("\"body\":\"") + 8;
            if (bodyStart <= 7) {
                // 尝试查找 "body":null 的情况
                int bodyNullStart = json.indexOf("\"body\":null");
                if (bodyNullStart > 0) {
                    return null;
                }
                return null;
            }

            // 提取 body 内容（需要处理转义字符）
            StringBuilder body = new StringBuilder();
            boolean escaped = false;
            for (int i = bodyStart; i < json.length(); i++) {
                char c = json.charAt(i);
                
                if (escaped) {
                    if (c == 'n') {
                        body.append('\n');
                    } else if (c == 'r') {
                        body.append('\r');
                    } else if (c == 't') {
                        body.append('\t');
                    } else if (c == '\\') {
                        body.append('\\');
                    } else if (c == '"') {
                        body.append('"');
                    } else {
                        body.append(c);
                    }
                    escaped = false;
                } else {
                    if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        // 遇到结束引号，停止解析
                        break;
                    } else {
                        body.append(c);
                    }
                }
            }

            String content = body.toString().trim();
            return content.isEmpty() ? null : content;
        } catch (Exception e) {
            return null;
        }
    }

    private HitokotoResult fetchHitokoto() {
        try {
            URI uri = URI.create(HITOKOTO_API_URL);
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
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("tooltip.portablestorage.easter_egg.desc"));
        
        // 显示详细信息（如果物品有存储的数据）
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_CONTENT)) {
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
        
        super.appendHoverText(stack, level, tooltip, type);
    }

    private static class HitokotoResult {
        String content;
        String from;
        String creator;
        String fromWho;
    }

    /**
     * 统一的消息结果类
     */
    private static class MessageResult {
        String content;
        String from;
        String creator;
        String fromWho;
        boolean isGitHubContent;

        MessageResult(String content, String from, String creator, String fromWho, boolean isGitHubContent) {
            this.content = content;
            this.from = from;
            this.creator = creator;
            this.fromWho = fromWho;
            this.isGitHubContent = isGitHubContent;
        }
    }
}
