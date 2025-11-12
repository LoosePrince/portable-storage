package com.portable.storage.client.screen;

import com.portable.storage.client.ClientConfig;
import com.portable.storage.sync.PlayerViewState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 统一的筛选/销毁界面
 * 支持筛选和销毁两种模式，显示规则列表
 */
public class FilterListScreen extends Screen {
    
    // 界面模式
    public enum Mode {
        FILTER,
        DESTROY;
        
        public String getDisplayName() {
            return switch (this) {
                case FILTER -> Text.translatable("portable-storage.filter.list.mode.filter").getString();
                case DESTROY -> Text.translatable("portable-storage.filter.list.mode.destroy").getString();
            };
        }
    }
    
    // 界面控件
    private TextFieldWidget filterInput;
    private CheckboxWidget whitelistMode;
    private CheckboxWidget blacklistMode;
    private ButtonWidget addButton;
    private ButtonWidget clearButton;
    private ButtonWidget backButton;
    
    // 界面状态
    private boolean isWhitelistMode = true;
    private String currentFilter = "";
    private List<ClientConfig.FilterRule> rules;
    
    // 滚动相关
    private float scroll = 0.0f;
    private int maxScrollRows = 0;
    
    // 界面布局常量
    private static final int BG_WIDTH = 500;
    private static final int BG_HEIGHT = 300;
    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 250;
    
    private static final int INPUT_WIDTH = 200;
    private static final int INPUT_HEIGHT = 20;
    
    private static final int ADD_BUTTON_WIDTH = 60;
    private static final int CLEAR_BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    
    private static final int BACK_BUTTON_WIDTH = 60;
    
    private static final int RULE_LIST_ITEM_HEIGHT = 20;
    private static final int RULE_LIST_VISIBLE_ITEMS = 8;
    private static final int RULE_LIST_HEIGHT = RULE_LIST_ITEM_HEIGHT * RULE_LIST_VISIBLE_ITEMS;
    
    // 滚动条相关
    private static final int SCROLLBAR_WIDTH = 8;
    
    public FilterListScreen(Mode mode) {
        super(Text.translatable("portable-storage.filter.list.title", mode.getDisplayName()));
        this.rules = mode == Mode.FILTER ? ClientConfig.getInstance().filterRules : ClientConfig.getInstance().destroyRules;
    }
    
    public FilterListScreen(Screen parent, Mode mode) {
        super(Text.translatable("portable-storage.filter.list.title", mode.getDisplayName()));
        this.parent = parent;
        this.rules = mode == Mode.FILTER ? ClientConfig.getInstance().filterRules : ClientConfig.getInstance().destroyRules;
    }
    
    public FilterListScreen(Screen parent, Mode mode, net.minecraft.util.math.BlockPos barrelPos) {
        super(Text.translatable("portable-storage.filter.list.title", mode.getDisplayName()));
        this.parent = parent;
        this.barrelPos = barrelPos;
        this.rules = new java.util.ArrayList<>(); // 绑定木桶的规则列表，从方块实体加载
    }
    
    private Screen parent = null;
    private net.minecraft.util.math.BlockPos barrelPos = null;
    
    // 自适应相关
    private float scale = 1.0f;
    private int actualWidth = BG_WIDTH;
    private int actualHeight = BG_HEIGHT;
    
    /**
     * 计算自适应缩放
     */
    private void calculateAdaptiveScale() {
        // 计算可用的缩放比例
        float scaleX = (float)(this.width - 40) / BG_WIDTH;  // 留20像素边距
        float scaleY = (float)(this.height - 40) / BG_HEIGHT;  // 留20像素边距
        
        // 使用较小的缩放比例，确保界面完全可见
        this.scale = Math.min(1.0f, Math.min(scaleX, scaleY));
        
        // 如果缩放后小于最小尺寸，使用最小尺寸
        if (BG_WIDTH * this.scale < MIN_WIDTH) {
            this.scale = (float)MIN_WIDTH / BG_WIDTH;
        }
        if (BG_HEIGHT * this.scale < MIN_HEIGHT) {
            this.scale = Math.max(this.scale, (float)MIN_HEIGHT / BG_HEIGHT);
        }
        
        // 计算实际尺寸
        this.actualWidth = (int)(BG_WIDTH * this.scale);
        this.actualHeight = (int)(BG_HEIGHT * this.scale);
    }
    
    @Override
    protected void init() {
        super.init();
        
        // 计算自适应缩放
        calculateAdaptiveScale();
        
        // 计算居中位置
        int centerX = (this.width - actualWidth) / 2;
        int centerY = (this.height - actualHeight) / 2;
        
        // 创建筛选输入框
        this.filterInput = new TextFieldWidget(
            this.textRenderer,
            centerX + (int)(20 * scale), centerY + (int)(20 * scale), 
            (int)(INPUT_WIDTH * scale), (int)(INPUT_HEIGHT * scale),
            Text.translatable("portable-storage.filter.list.input")
        );
        this.filterInput.setMaxLength(256);
        this.filterInput.setPlaceholder(Text.translatable("portable-storage.filter.list.input.placeholder"));
        this.filterInput.setText(currentFilter);
        this.addDrawableChild(filterInput);
        
        // 创建模式选择（单选框）
        this.whitelistMode = CheckboxWidget.builder(
            Text.translatable("portable-storage.filter.list.whitelist"),
            this.textRenderer
        ).pos(centerX + (int)(240 * scale), centerY + (int)(20 * scale)).checked(isWhitelistMode).callback((checkbox, checked) -> {
            if (checked) {
                isWhitelistMode = true;
                updateModeSelection();
            }
        }).build();
        
        this.blacklistMode = CheckboxWidget.builder(
            Text.translatable("portable-storage.filter.list.blacklist"),
            this.textRenderer
        ).pos(centerX + (int)(320 * scale), centerY + (int)(20 * scale)).checked(!isWhitelistMode).callback((checkbox, checked) -> {
            if (checked) {
                isWhitelistMode = false;
                updateModeSelection();
            }
        }).build();
        
        // 创建按钮
        this.addButton = ButtonWidget.builder(
            Text.translatable("portable-storage.filter.list.add"),
            button -> addRule()
        ).dimensions(centerX + (int)(20 * scale), centerY + (int)(50 * scale), 
            (int)(ADD_BUTTON_WIDTH * scale), (int)(BUTTON_HEIGHT * scale)).build();
        
        this.clearButton = ButtonWidget.builder(
            Text.translatable("portable-storage.filter.list.clear"),
            button -> clearAllRules()
        ).dimensions(centerX + (int)(90 * scale), centerY + (int)(50 * scale), 
            (int)(CLEAR_BUTTON_WIDTH * scale), (int)(BUTTON_HEIGHT * scale)).build();
        
        this.backButton = ButtonWidget.builder(
            Text.translatable("portable-storage.filter.list.back"),
            button -> {
                if (parent != null) {
                    MinecraftClient.getInstance().setScreen(parent);
                } else {
                    close();
                }
            }
        ).dimensions(centerX + (int)((BG_WIDTH - 80) * scale), centerY + (int)((BG_HEIGHT - 30) * scale), 
            (int)(BACK_BUTTON_WIDTH * scale), (int)(BUTTON_HEIGHT * scale)).build();
        
        this.addDrawableChild(whitelistMode);
        this.addDrawableChild(blacklistMode);
        this.addDrawableChild(addButton);
        this.addDrawableChild(clearButton);
        this.addDrawableChild(backButton);
        
        // 如果是绑定木桶模式，从方块实体加载规则
        if (barrelPos != null) {
            loadRulesFromBarrel();
        } else {
            // 初始化时同步规则到服务器（玩家模式）
            syncRulesToServer();
        }
        
        // 标记开始查看仓库界面
        if (MinecraftClient.getInstance().player != null) {
            PlayerViewState.startViewing(MinecraftClient.getInstance().player.getUuid());
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先调用super.render()渲染背景和控件
        super.render(context, mouseX, mouseY, delta);
        
        // 然后在最上层渲染文字，确保不被模糊层覆盖
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // 渲染规则列表
        renderRuleList(context);
    }
    
    /**
     * 渲染规则列表
     */
    private void renderRuleList(DrawContext context) {
        // 计算居中位置
        int centerX = (this.width - actualWidth) / 2;
        int centerY = (this.height - actualHeight) / 2;
        
        int startY = centerY + (int)(76 * scale);
        int itemHeight = (int)(RULE_LIST_ITEM_HEIGHT * scale);
        
        // 计算滚动参数
        updateScrollParameters();
        
        // 渲染表头
        context.drawTextWithShadow(this.textRenderer, Text.translatable("portable-storage.filter.list.header.rule"), centerX + (int)(20 * scale), startY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("portable-storage.filter.list.header.mode"), centerX + (int)(170 * scale), startY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("portable-storage.filter.list.header.status"), centerX + (int)(250 * scale), startY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("portable-storage.filter.list.header.action"), centerX + (int)(320 * scale), startY, 0xFFFFFF);
        
        // 渲染分隔线
        context.fill(centerX + (int)(20 * scale), startY + (int)(12 * scale), 
            centerX + (int)((BG_WIDTH - 20) * scale), startY + (int)(13 * scale), 0xFFFFFFFF);
        
        // 渲染规则列表（带滚动）
        int visibleStart = (int)(scroll * maxScrollRows);
        int visibleEnd = Math.min(visibleStart + RULE_LIST_VISIBLE_ITEMS, rules.size());
        
        for (int i = visibleStart; i < visibleEnd; i++) {
            ClientConfig.FilterRule rule = rules.get(i);
            int y = startY + (int)(20 * scale) + (i - visibleStart) * itemHeight;
            
            // 匹配规则
            String displayRule = rule.matchRule.length() > 20 ? 
                rule.matchRule.substring(0, 17) + "..." : rule.matchRule;
            context.drawTextWithShadow(this.textRenderer, displayRule, centerX + (int)(20 * scale), y, 0xFFFFFF);
            
            // 模式
            Text modeText = rule.isWhitelist ? 
                Text.translatable("portable-storage.filter.list.mode.whitelist") : 
                Text.translatable("portable-storage.filter.list.mode.blacklist");
            context.drawTextWithShadow(this.textRenderer, modeText, centerX + (int)(170 * scale), y, 
                rule.isWhitelist ? 0x00FF00 : 0xFF0000);
            
            // 状态（可点击切换）
            Text statusText = rule.enabled ? 
                Text.translatable("portable-storage.filter.list.status.enabled") : 
                Text.translatable("portable-storage.filter.list.status.disabled");
            context.drawTextWithShadow(this.textRenderer, statusText, centerX + (int)(250 * scale), y, 
                rule.enabled ? 0x00FF00 : 0xFF0000);
            
            // 删除按钮
            context.drawTextWithShadow(this.textRenderer, Text.translatable("portable-storage.filter.list.action.delete"), centerX + (int)(320 * scale), y, 0xFF0000);
        }
        
        // 渲染滚动条
        if (maxScrollRows > 0) {
            drawScrollbar(context, centerX, centerY);
        }
    }
    
    @Override
    public void close() {
        // 标记停止查看仓库界面
        if (MinecraftClient.getInstance().player != null) {
            PlayerViewState.stopViewing(MinecraftClient.getInstance().player.getUuid());
        }
        super.close();
    }
    
    private void addRule() {
        String matchRule = filterInput.getText().trim();
        if (!matchRule.isEmpty()) {
            ClientConfig.FilterRule rule = new ClientConfig.FilterRule(matchRule, isWhitelistMode, true);
            rules.add(rule);
            
            if (barrelPos != null) {
                // 绑定木桶模式：保存到绑定木桶
                saveRulesToBarrel();
            } else {
                // 玩家模式：保存到配置文件
                ClientConfig.save();
                // 同步规则到服务器
                syncRulesToServer();
            }
            
            // 清空输入框
            filterInput.setText("");
            currentFilter = "";
        }
    }
    
    private void clearAllRules() {
        rules.clear();
        
        if (barrelPos != null) {
            // 绑定木桶模式：保存到绑定木桶
            saveRulesToBarrel();
        } else {
            // 玩家模式：保存到配置文件
            ClientConfig.save();
            // 同步规则到服务器
            syncRulesToServer();
        }
    }
    
    /**
     * 从绑定木桶方块实体加载规则
     */
    private void loadRulesFromBarrel() {
        if (barrelPos == null) return;
        
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.world == null) return;
        
        net.minecraft.block.entity.BlockEntity blockEntity = client.world.getBlockEntity(barrelPos);
        if (blockEntity instanceof com.portable.storage.blockentity.BoundBarrelBlockEntity barrel) {
            // 加载筛选规则
            rules.clear();
            java.util.List<com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule> barrelRules = barrel.getFilterRules();
            for (com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule rule : barrelRules) {
                rules.add(new ClientConfig.FilterRule(rule.matchRule, rule.isWhitelist, rule.enabled));
            }
        }
    }
    
    /**
     * 保存规则到绑定木桶
     */
    private void saveRulesToBarrel() {
        if (barrelPos == null) return;
        
        // 获取当前筛选规则
        java.util.List<com.portable.storage.net.payload.SyncBarrelFilterRulesC2SPayload.FilterRule> filterRules = new java.util.ArrayList<>();
        
        // 从绑定木桶方块实体获取当前规则
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.world != null) {
            net.minecraft.block.entity.BlockEntity blockEntity = client.world.getBlockEntity(barrelPos);
            if (blockEntity instanceof com.portable.storage.blockentity.BoundBarrelBlockEntity barrel) {
                // 获取现有的筛选规则
                for (com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule rule : barrel.getFilterRules()) {
                    filterRules.add(new com.portable.storage.net.payload.SyncBarrelFilterRulesC2SPayload.FilterRule(
                        rule.matchRule, rule.isWhitelist, rule.enabled
                    ));
                }
            }
        }
        
        // 更新筛选规则列表
        filterRules.clear();
        for (ClientConfig.FilterRule rule : rules) {
            filterRules.add(new com.portable.storage.net.payload.SyncBarrelFilterRulesC2SPayload.FilterRule(
                rule.matchRule, rule.isWhitelist, rule.enabled
            ));
        }
        
        // 发送网络包到服务器
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.portable.storage.net.payload.SyncBarrelFilterRulesC2SPayload(barrelPos, filterRules)
        );
        
        // 同时更新客户端的方块实体，确保界面切换时规则不会丢失
        if (client.world != null) {
            net.minecraft.block.entity.BlockEntity blockEntity = client.world.getBlockEntity(barrelPos);
            if (blockEntity instanceof com.portable.storage.blockentity.BoundBarrelBlockEntity barrel) {
                // 转换规则格式并更新客户端方块实体
                java.util.List<com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule> clientFilterRules = new java.util.ArrayList<>();
                for (com.portable.storage.net.payload.SyncBarrelFilterRulesC2SPayload.FilterRule rule : filterRules) {
                    clientFilterRules.add(new com.portable.storage.blockentity.BoundBarrelBlockEntity.FilterRule(
                        rule.matchRule(), rule.isWhitelist(), rule.enabled()
                    ));
                }
                
                barrel.setFilterRules(clientFilterRules);
            }
        }
    }
    
    /**
     * 更新滚动参数
     */
    private void updateScrollParameters() {
        maxScrollRows = Math.max(0, rules.size() - RULE_LIST_VISIBLE_ITEMS);
        if (maxScrollRows <= 0) {
            scroll = 0.0f;
        }
    }
    
    /**
     * 绘制滚动条
     */
    private void drawScrollbar(DrawContext context, int centerX, int centerY) {
        int scrollbarX = centerX + (int)((BG_WIDTH - 30) * scale);
        int scrollbarY = centerY + (int)(96 * scale);
        int scrollbarHeight = (int)(RULE_LIST_HEIGHT * scale);
        int scrollbarWidth = (int)(SCROLLBAR_WIDTH * scale);
        
        // 轨道
        int track = 0x55000000;
        context.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, track);

        // 滑块高度与可视比例相关
        int visible = RULE_LIST_VISIBLE_ITEMS;
        int total = Math.max(visible, rules.size());
        float frac = (float)visible / (float)total;
        int thumbMin = (int)(8 * scale);
        int thumbHeight = Math.max(thumbMin, (int)(scrollbarHeight * frac));

        int maxScrollPx = scrollbarHeight - thumbHeight;
        int thumbOffset = (maxScrollRows <= 0) ? 0 : (int)(scroll * maxScrollPx);
        int thumbTop = scrollbarY + thumbOffset;

        int thumbBg = 0xFF999999;
        int thumbDark = 0xFF555555;
        int thumbLight = 0xFFFFFFFF;
        context.fill(scrollbarX, thumbTop, scrollbarX + scrollbarWidth, thumbTop + thumbHeight, thumbBg);
        // 边框（内凹效果）
        context.fill(scrollbarX, thumbTop, scrollbarX + scrollbarWidth, thumbTop + 1, thumbDark);
        context.fill(scrollbarX + scrollbarWidth - 1, thumbTop, scrollbarX + scrollbarWidth, thumbTop + thumbHeight, thumbDark);
        context.fill(scrollbarX, thumbTop + thumbHeight - 1, scrollbarX + scrollbarWidth, thumbTop + thumbHeight, thumbLight);
        context.fill(scrollbarX, thumbTop, scrollbarX + 1, thumbTop + thumbHeight, thumbLight);
    }
    
    /**
     * 更新模式选择状态（单选框行为）
     */
    private void updateModeSelection() {
        // 计算居中位置
        int centerX = (this.width - actualWidth) / 2;
        int centerY = (this.height - actualHeight) / 2;
        
        // 移除旧的复选框
        remove(whitelistMode);
        remove(blacklistMode);
        
        // 重新创建复选框，确保状态同步
        this.whitelistMode = CheckboxWidget.builder(
            Text.translatable("portable-storage.filter.list.whitelist"),
            this.textRenderer
        ).pos(centerX + (int)(240 * scale), centerY + (int)(20 * scale)).checked(isWhitelistMode).callback((checkbox, checked) -> {
            if (checked) {
                isWhitelistMode = true;
                updateModeSelection();
            }
        }).build();
        
        this.blacklistMode = CheckboxWidget.builder(
            Text.translatable("portable-storage.filter.list.blacklist"),
            this.textRenderer
        ).pos(centerX + (int)(320 * scale), centerY + (int)(20 * scale)).checked(!isWhitelistMode).callback((checkbox, checked) -> {
            if (checked) {
                isWhitelistMode = false;
                updateModeSelection();
            }
        }).build();
        
        // 重新添加到界面
        addDrawableChild(whitelistMode);
        addDrawableChild(blacklistMode);
    }
    
    /**
     * 同步规则到服务器
     */
    private void syncRulesToServer() {
        if (MinecraftClient.getInstance().player == null) return;
        
        // 转换规则格式
        java.util.List<com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule> serverFilterRules = new java.util.ArrayList<>();
        java.util.List<com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule> serverDestroyRules = new java.util.ArrayList<>();
        
        for (ClientConfig.FilterRule rule : ClientConfig.getInstance().filterRules) {
            serverFilterRules.add(new com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule(
                rule.matchRule, rule.isWhitelist, rule.enabled
            ));
        }
        
        for (ClientConfig.FilterRule rule : ClientConfig.getInstance().destroyRules) {
            serverDestroyRules.add(new com.portable.storage.net.payload.SyncFilterRulesC2SPayload.FilterRule(
                rule.matchRule, rule.isWhitelist, rule.enabled
            ));
        }
        
        // 发送到服务器
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.portable.storage.net.payload.SyncFilterRulesC2SPayload(serverFilterRules, serverDestroyRules)
        );
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 检查是否在规则列表区域
        if (isOverRuleListArea(mouseX, mouseY)) {
            if (maxScrollRows > 0) {
                float delta = (float)verticalAmount * -0.1f;
                scroll = Math.max(0.0f, Math.min(1.0f, scroll + delta));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    /**
     * 检查鼠标是否在规则列表区域
     */
    private boolean isOverRuleListArea(double mouseX, double mouseY) {
        int centerX = (this.width - actualWidth) / 2;
        int centerY = (this.height - actualHeight) / 2;
        int startY = centerY + (int)(76 * scale);
        int scrollbarX = centerX + (int)((BG_WIDTH - 30) * scale);
        int scrollbarWidth = (int)(SCROLLBAR_WIDTH * scale);
        
        return mouseX >= centerX + (int)(20 * scale) && mouseX <= scrollbarX + scrollbarWidth &&
               mouseY >= startY && mouseY <= startY + (int)(RULE_LIST_HEIGHT * scale) + (int)(20 * scale);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 计算居中位置
        int centerX = (this.width - actualWidth) / 2;
        int centerY = (this.height - actualHeight) / 2;
        
        // 处理规则列表的点击事件
        int startY = centerY + (int)(76 * scale);
        int itemHeight = (int)(RULE_LIST_ITEM_HEIGHT * scale);
        int visibleStart = (int)(scroll * maxScrollRows);
        int visibleEnd = Math.min(visibleStart + RULE_LIST_VISIBLE_ITEMS, rules.size());
        
        for (int i = visibleStart; i < visibleEnd; i++) {
            int y = startY + (int)(20 * scale) + (i - visibleStart) * itemHeight;
            
            // 检查状态切换点击
            if (mouseX >= centerX + (int)(250 * scale) && mouseX <= centerX + (int)(320 * scale) && 
                mouseY >= y && mouseY <= y + itemHeight) {
                ClientConfig.FilterRule rule = rules.get(i);
                rule.enabled = !rule.enabled;
                
                if (barrelPos != null) {
                    // 绑定木桶模式：保存到绑定木桶
                    saveRulesToBarrel();
                } else {
                    // 玩家模式：保存到配置文件
                    ClientConfig.save();
                    // 同步规则到服务器
                    syncRulesToServer();
                }
                return true;
            }
            
            // 检查删除按钮点击
            if (mouseX >= centerX + (int)(320 * scale) && mouseX <= centerX + (int)(370 * scale) && 
                mouseY >= y && mouseY <= y + itemHeight) {
                rules.remove(i);
                
                if (barrelPos != null) {
                    // 绑定木桶模式：保存到绑定木桶
                    saveRulesToBarrel();
                } else {
                    // 玩家模式：保存到配置文件
                    ClientConfig.save();
                    // 同步规则到服务器
                    syncRulesToServer();
                }
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
