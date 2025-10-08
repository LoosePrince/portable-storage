package com.portable.storage.client.ui;

import com.portable.storage.client.ClientConfig;
import com.portable.storage.client.ClientStorageState;
import com.portable.storage.client.ClientUpgradeState;
import com.portable.storage.net.payload.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 仓库UI组件，可在不同界面复用
 */
public class StorageUIComponent {
    // 纹理
    private static final Identifier TEX_BG = Identifier.of("portable-storage", "textures/gui/portable_storage_gui_1.png");
    private static final Identifier TEX_SETTINGS_BG = Identifier.of("portable-storage", "textures/gui/portable_storage_gui.png");
    private static final Identifier TEX_SLOT = Identifier.of("portable-storage", "textures/gui/slot.png");
    
    // 切换原版界面回调
    private Runnable switchToVanillaCallback = null;
    // 仓库网格参数
    private final int cols = 9;
    private final int visibleRows = 6;
    private final int slotSize = 18;
    private final int slotSpacing = 0;
    
    // 升级槽位参数
    private final int upgradeSlotSize = 18;
    private final int upgradeSpacing = 0;
    private final int upgradeCount = 5;
    
    // 注意：升级物品类型现在由UpgradeInventory.getExpectedUpgradeForSlot()决定
    
    // UI状态
    private float scroll = 0.0f;
    private boolean dragScrollbar = false;
    private int dragGrabOffset = 0;
    private int totalRows = 12;
    private List<Integer> filteredIndices = new ArrayList<>();
    private int[] visibleIndexMap = new int[0];
    private String query = "";
    private boolean collapsed = false;
    
    // 折叠态点击区域
    private int expandTabLeft, expandTabTop, expandTabRight, expandTabBottom;
    
    // 折叠按钮点击区域
    private int collapseLeft, collapseTop, collapseRight, collapseBottom;
    
    // 搜索框
    private TextFieldWidget searchField;
    
    // UI位置（由外部设置）
    private int gridLeft, gridTop;
    private int baseX, baseY, baseBgW, baseBgH;
    private int scrollbarLeft, scrollbarTop, scrollbarHeight, scrollbarWidth;
    
    // 升级槽位点击区域
    private final int[] upgradeSlotLefts = new int[5];
    private final int[] upgradeSlotTops = new int[5];
    private final int[] upgradeSlotRights = new int[5];
    private final int[] upgradeSlotBottoms = new int[5];
    
    // 升级说明按钮
    private int upgradeHelpLeft;
    private int upgradeHelpTop;
    private int upgradeHelpRight;
    private int upgradeHelpBottom;
    
    // 设置面板点击区域
    private int sortModeLeft, sortModeTop, sortModeRight, sortModeBottom;
    private int sortOrderLeft, sortOrderTop, sortOrderRight, sortOrderBottom;
    private int craftRefillLeft, craftRefillTop, craftRefillRight, craftRefillBottom;
    private int autoDepositLeft, autoDepositTop, autoDepositRight, autoDepositBottom;
    private int searchPosLeft, searchPosTop, searchPosRight, searchPosBottom;
    
    // 切换原版界面点击区域
    private int switchVanillaLeft, switchVanillaTop, switchVanillaRight, switchVanillaBottom;
    
    // 缓存上次的排序配置
    private ClientConfig.SortMode lastSortMode = ClientConfig.SortMode.COUNT;
    private boolean lastSortAscending = false;
    
    public StorageUIComponent() {
        resetSortCache();
    }

    /**
     * 重置排序缓存
     */
    public void resetSortCache() {
        ClientConfig config = ClientConfig.getInstance();
        lastSortMode = config.sortMode;
        lastSortAscending = config.sortAscending;
    }
    
    /**
     * 设置切换原版界面的回调
     */
    public void setSwitchToVanillaCallback(Runnable callback) {
        this.switchToVanillaCallback = callback;
    }
    
    /**
     * 初始化搜索框
     */
    public void initSearchField(MinecraftClient client, int x, int y, int width, int height) {
        this.searchField = new TextFieldWidget(
            client.textRenderer,
            x, y, width, height,
            Text.translatable("portable_storage.search")
        );
        this.searchField.setPlaceholder(Text.translatable("portable_storage.search.placeholder"));
        this.searchField.setSuggestion(Text.translatable("portable_storage.search.placeholder").getString());
        this.searchField.setMaxLength(64);
        this.searchField.setEditable(true);
        this.searchField.setVisible(true);
        this.searchField.setDrawsBackground(true);
        this.searchField.setEditableColor(0xFFFFFF);
        this.searchField.setChangedListener(text -> {
            this.query = text == null ? "" : text;
            this.scroll = 0.0f;
            if (text == null || text.isEmpty()) {
                this.searchField.setSuggestion(Text.translatable("portable_storage.search.placeholder").getString());
            } else {
                this.searchField.setSuggestion("");
            }
        });
    }
    
    /**
     * 渲染仓库UI（支持折叠功能，仅在背包界面使用）
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta, int screenX, int screenY, int backgroundWidth, int backgroundHeight, boolean enableCollapse) {
        // 每次渲染前重置缓存，确保排序配置改变时能正确响应
        resetSortCache();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        
        // 检查仓库是否已启用
        if (!ClientStorageState.isStorageEnabled()) {
            return;
        }
        
        // 计算位置
        this.baseX = screenX;
        this.baseY = screenY;
        this.baseBgW = backgroundWidth;
        this.baseBgH = backgroundHeight;
        this.gridLeft = screenX + 8;
        int gapBelow = 6;
        int extraYOffset = 0;
        int searchH = (this.searchField != null ? this.searchField.getHeight() : 18);
        if (ClientConfig.getInstance().searchPos == ClientConfig.SearchPos.MIDDLE) {
            extraYOffset = searchH + 1; // 让仓库整体下移，给搜索框让位
        }
        this.gridTop = screenY + backgroundHeight + gapBelow + extraYOffset;
        
        // 折叠态：只显示一个"展开仓库"的小块
        if (enableCollapse && collapsed) {
            int tabW = 72;
            int tabH = 18;
            int tabLeft = screenX + (backgroundWidth - tabW) / 2; // 居中到背包下方
            int tabTop = screenY + backgroundHeight + 6;
            this.expandTabLeft = tabLeft;
            this.expandTabTop = tabTop;
            this.expandTabRight = tabLeft + tabW;
            this.expandTabBottom = tabTop + tabH;
            drawPanel(context, tabLeft, tabTop, tabW, tabH);
            Text label = Text.translatable("portable_storage.ui.expand");
            int textW = client.textRenderer.getWidth(label);
            int tx = tabLeft + (tabW - textW) / 2;
            int ty = tabTop + (tabH - 8) / 2;
            context.drawText(client.textRenderer, label, tx, ty, 0xFFFFFF, true);
            return;
        }
        
        int upgradeLeft = screenX - 24;
        
        ClientConfig config = ClientConfig.getInstance();
        int panelWidth = calculatePanelWidth(client, config, enableCollapse);
        int panelLeft = screenX + backgroundWidth + 6;
        
        int extLeft = upgradeLeft - 2;
        int extTop = gridTop - 2;
        int extRight = panelLeft + panelWidth + 2;
        int extBottom = gridTop + visibleRows * (slotSize + slotSpacing) + 2;
        
        // 绘制扩展背景（左上偏移，九宫格拉伸中心区域）
        final int bgOffsetX = 4;
        final int bgOffsetY = 4;
        drawExtensionBackground(context, extLeft - bgOffsetX, extTop - bgOffsetY, (extRight - extLeft) + bgOffsetX * 2, (extBottom - extTop) + bgOffsetY * 2);
        
        // 构建过滤索引并渲染仓库网格
        renderStorageGrid(context, client, mouseX, mouseY);
        
        // 渲染滚动条
        renderScrollbar(context);
        
        // 更新拖动状态
        updateScrollbarDrag(mouseX, mouseY);
        
        // 渲染升级槽位
        renderUpgradeSlots(context, upgradeLeft);
        
        // 渲染设置面板（无背景）
        renderSettingsPanel(context, client, config, panelLeft, panelWidth, enableCollapse);
        
        // 渲染搜索框
        updateSearchFieldPosition();
        if (this.searchField != null) {
            this.searchField.render(context, mouseX, mouseY, delta);
        }
    }
    
    /**
     * 渲染仓库UI（不支持折叠，用于工作台等界面）
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta, int screenX, int screenY, int backgroundWidth, int backgroundHeight) {
        render(context, mouseX, mouseY, delta, screenX, screenY, backgroundWidth, backgroundHeight, false);
    }
    
    
    /**
     * 渲染仓库网格
     */
    private void renderStorageGrid(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
        List<Integer> filtered = buildFilteredIndices();
        this.filteredIndices = filtered;
        int filteredSize = filtered.size();
        this.totalRows = Math.max(visibleRows, (int)Math.ceil(filteredSize / (double)cols));
        int maxScrollRows = Math.max(0, totalRows - visibleRows);
        int rowOffset = (int)Math.floor(scroll * maxScrollRows + 0.5f);
        rowOffset = Math.max(0, Math.min(maxScrollRows, rowOffset));
        
        ItemStack hoveredStack = ItemStack.EMPTY;
        int hoveredIndex = -1;
        
        if (visibleIndexMap.length != visibleRows * cols) {
            visibleIndexMap = new int[visibleRows * cols];
        }
        java.util.Arrays.fill(visibleIndexMap, -1);
        
        for (int row = 0; row < visibleRows; row++) {
            int modelRow = row + rowOffset;
            // 不再提前 break；保证至少绘制 visibleRows 行的槽位背景
            for (int col = 0; col < cols; col++) {
                int sx = gridLeft + col * (slotSize + slotSpacing);
                int sy = gridTop + row * (slotSize + slotSpacing);
                drawSlotInset(context, sx, sy, slotSize, slotSize);
                
                int filteredIndex = modelRow * cols + col;
                if (modelRow < totalRows && filteredIndex >= 0 && filteredIndex < filteredSize) {
                    int storageIndex = filtered.get(filteredIndex);
                    var stacks = ClientStorageState.getStacks();
                    if (storageIndex >= 0 && storageIndex < stacks.size()) {
                        var stack = stacks.get(storageIndex);
                        if (stack != null && !stack.isEmpty()) {
                            context.drawItem(stack, sx + 1, sy + 1);
                            String countText = formatCount(ClientStorageState.getCount(storageIndex) > 0 ? 
                                (int)Math.min(Integer.MAX_VALUE, ClientStorageState.getCount(storageIndex)) : stack.getCount());
                            float scale = 0.75f;
                            int textWidth = client.textRenderer.getWidth(countText);
                            int txUnscaled = sx + slotSize - 1 - (int)(textWidth * scale);
                            int tyUnscaled = sy + slotSize - (int)(9 * scale);
                            context.getMatrices().push();
                            context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                            context.getMatrices().scale(scale, scale, 1.0f);
                            context.drawText(client.textRenderer, countText, (int)(txUnscaled / scale), (int)(tyUnscaled / scale), 0xFFFFFF, true);
                            context.getMatrices().pop();
                            
                            if (hoveredStack.isEmpty() && mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                                hoveredStack = stack;
                                hoveredIndex = storageIndex;
                            }
                            visibleIndexMap[row * cols + col] = storageIndex;
                        }
                    }
                }
            }
        }
        
        // 检查是否悬停在升级说明按钮上
        if (isIn(mouseX, mouseY, upgradeHelpLeft, upgradeHelpTop, upgradeHelpRight, upgradeHelpBottom)) {
            List<Text> helpLines = List.of(
                Text.translatable("portable_storage.ui.upgrade_help.title"),
                Text.empty(),
                Text.translatable("portable_storage.ui.upgrade_help.crafting_table"),
                Text.translatable("portable_storage.ui.upgrade_help.hopper"),
                Text.translatable("portable_storage.ui.upgrade_help.chest"),
                Text.translatable("portable_storage.ui.upgrade_help.barrel")
            );
            context.drawTooltip(client.textRenderer, helpLines, mouseX, mouseY);
        }
        // 检查是否悬停在升级槽位上
        else if (portableStorage$isOverUpgradeSlot(mouseX, mouseY)) {
            int slotIndex = portableStorage$getHoveredUpgradeSlot(mouseX, mouseY);
            if (slotIndex >= 0) {
                ItemStack stack = ClientUpgradeState.getStack(slotIndex);
                String upgradeName = portableStorage$getUpgradeName(slotIndex);
                boolean isDisabled = ClientUpgradeState.isSlotDisabled(slotIndex);

                List<Text> tooltipLines = new java.util.ArrayList<>();
                tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_slot", slotIndex + 1));
                tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_name", upgradeName));

                if (!stack.isEmpty()) {
                    if (isDisabled) {
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_status", "✗").formatted(net.minecraft.util.Formatting.RED));
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_disabled"));
                    } else {
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_status", "✓").formatted(net.minecraft.util.Formatting.GREEN));
                    }
                } else {
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_status", "✗"));
                }
                
                // 添加右键提示
                tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_right_click_hint"));

                context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
            }
        }
        // 检查是否悬停在物品上
        else if (!hoveredStack.isEmpty()) {
            List<Text> lines = net.minecraft.client.gui.screen.Screen.getTooltipFromItem(client, hoveredStack);
            if (hoveredIndex >= 0) {
                long exact = ClientStorageState.getCount(hoveredIndex);
                if (exact > 999) {
                    String exactStr = String.format(Locale.US, "%,d", exact);
                    lines.add(1, Text.literal(exactStr));
                }

                // 添加修改时间信息
                long timestamp = ClientStorageState.getTimestamp(hoveredIndex);
                if (timestamp > 0) {
                    String timeStr = formatTimestamp(timestamp);
                    lines.add(1, Text.translatable("portable_storage.tooltip.last_modified", timeStr));
                }
            }
            context.drawTooltip(client.textRenderer, lines, mouseX, mouseY);
        }
    }
    
    /**
     * 渲染滚动条
     */
    private void renderScrollbar(DrawContext context) {
        int trackLeft = gridLeft + cols * (slotSize + slotSpacing) + 4;
        int trackTop = gridTop;
        int trackHeight = visibleRows * (slotSize + slotSpacing);
        int trackWidth = 6;
        
        this.scrollbarLeft = trackLeft;
        this.scrollbarTop = trackTop;
        this.scrollbarHeight = trackHeight;
        this.scrollbarWidth = trackWidth;
        
        int maxScrollRows = Math.max(0, totalRows - visibleRows);
        drawScrollbar(context, trackLeft, trackTop, trackWidth, trackHeight, maxScrollRows);
    }
    
    /**
     * 更新滚动条拖动状态
     */
    private void updateScrollbarDrag(double mouseX, double mouseY) {
        if (dragScrollbar) {
            MinecraftClient mc = MinecraftClient.getInstance();
            long window = mc != null && mc.getWindow() != null ? mc.getWindow().getHandle() : 0L;
            boolean leftDown = false;
            if (window != 0L) {
                leftDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
            if (!leftDown) {
                dragScrollbar = false;
            } else {
                int height = this.scrollbarHeight;
                int visible = this.visibleRows;
                int total = Math.max(visible, this.totalRows);
                int thumbMin = 8;
                int thumbHeight = Math.max(thumbMin, (int)(height * ((float)visible / (float)total)));
                int maxScrollPx = Math.max(1, height - thumbHeight);
                int top = this.scrollbarTop;
                int mouseOffset = (int)mouseY - top - dragGrabOffset;
                float newScroll = (float)mouseOffset / (float)maxScrollPx;
                this.scroll = Math.max(0.0f, Math.min(1.0f, newScroll));
                ClientPlayNetworking.send(new ScrollC2SPayload(this.scroll));
            }
        }
    }
    
    /**
     * 渲染升级槽位
     */
    private void renderUpgradeSlots(DrawContext context, int upgradeLeft) {
        int upgradeTop = gridTop;
        for (int i = 0; i < upgradeCount; i++) {
            int sx = upgradeLeft;
            int sy = upgradeTop + i * (upgradeSlotSize + upgradeSpacing);
            drawSlotInset(context, sx, sy, upgradeSlotSize, upgradeSlotSize);
            
            upgradeSlotLefts[i] = sx;
            upgradeSlotTops[i] = sy;
            upgradeSlotRights[i] = sx + upgradeSlotSize;
            upgradeSlotBottoms[i] = sy + upgradeSlotSize;
            
            ItemStack upgradeStack = ClientUpgradeState.getStack(i);
            ItemStack expectedStack = com.portable.storage.storage.UpgradeInventory.getExpectedUpgradeForSlot(i);
            
            if (!upgradeStack.isEmpty()) {
                // 有物品，正常渲染
                context.drawItem(upgradeStack, sx + 1, sy + 1);
                
                // 如果槽位被禁用，添加红色半透明遮罩
                if (ClientUpgradeState.isSlotDisabled(i)) {
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, 200); // 提高层级
                    context.fill(sx + 1, sy + 1, sx + upgradeSlotSize - 1, sy + upgradeSlotSize - 1, 0x80FF0000);
                    context.getMatrices().pop();
                }
            } else {
                // 空槽位，显示预期物品图标并叠加白色半透明遮罩
                context.drawItem(expectedStack, sx + 1, sy + 1);
                
                // 叠加白色半透明遮罩（在物品图标上方）
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200); // 提高层级
                context.fill(sx + 1, sy + 1, sx + upgradeSlotSize - 1, sy + upgradeSlotSize - 1, 0x80FFFFFF);
                context.getMatrices().pop();
            }
        }
        
        // 添加升级说明按钮（第6个槽位位置）
        int helpButtonSize = 16;
        int helpX = upgradeLeft + 1;
        int helpY = upgradeTop + upgradeCount * (upgradeSlotSize + upgradeSpacing) + 2;
        
        // 绘制按钮背景
        context.fill(helpX, helpY, helpX + helpButtonSize, helpY + helpButtonSize, 0xFF404040);
        
        // 绘制"？"文本
        String helpText = "?";
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = textRenderer.getWidth(helpText);
        int textX = helpX + (helpButtonSize - textWidth) / 2;
        int textY = helpY + (helpButtonSize - 8) / 2;
        context.drawText(textRenderer, helpText, textX, textY, 0xFFFFFF, true);
        
        // 记录按钮位置
        upgradeHelpLeft = helpX;
        upgradeHelpTop = helpY;
        upgradeHelpRight = helpX + helpButtonSize;
        upgradeHelpBottom = helpY + helpButtonSize;
    }
    
    /**
     * 渲染设置面板
     */
    private void renderSettingsPanel(DrawContext context, MinecraftClient client, ClientConfig config, int panelLeft, int panelWidth, boolean enableCollapse) {
        int panelTop = gridTop;
        int panelBottom = gridTop + visibleRows * (slotSize + slotSpacing);
        drawSettingsBackground(context, panelLeft, panelTop, panelWidth, panelBottom - panelTop);
        
        int textX = panelLeft + 6;
        int textY = panelTop + 6;
        context.drawText(client.textRenderer, Text.translatable("portable_storage.ui.settings"), textX, textY, 0xFFFFFF, true);
        textY += 14;
        
        int collapseTextH = 9;
        
        // 折叠仓库（仅在背包界面显示）
        if (enableCollapse) {
            Text collapseText = Text.translatable("portable_storage.ui.collapse");
            int collapseTextW = client.textRenderer.getWidth(collapseText);
            context.drawText(client.textRenderer, collapseText, textX, textY, 0xFFFFFF, true);
            this.collapseLeft = textX;
            this.collapseTop = textY - 1;
            this.collapseRight = textX + collapseTextW + 2;
            this.collapseBottom = textY + collapseTextH + 3;
            textY += 12;
        }
        
        // 排序模式
        String sortModeKey = switch (config.sortMode) {
            case COUNT -> "portable_storage.sort.count";
            case NAME -> "portable_storage.sort.name";
            case MOD_ID -> "portable_storage.sort.mod_id";
            case UPDATE_TIME -> "portable_storage.sort.update_time";
        };
        Text sortModeText = Text.translatable("portable_storage.ui.sort_mode", Text.translatable(sortModeKey).getString());
        int sortModeTextW = client.textRenderer.getWidth(sortModeText);
        context.drawText(client.textRenderer, sortModeText, textX, textY, 0xFFFFFF, true);
        this.sortModeLeft = textX;
        this.sortModeTop = textY - 1;
        this.sortModeRight = textX + sortModeTextW + 2;
        this.sortModeBottom = textY + collapseTextH + 3;
        textY += 12;
        
        // 排序顺序
        String sortOrderKey = config.sortAscending ? "portable_storage.sort.ascending" : "portable_storage.sort.descending";
        Text sortOrderText = Text.translatable("portable_storage.ui.sort_order", Text.translatable(sortOrderKey).getString());
        int sortOrderTextW = client.textRenderer.getWidth(sortOrderText);
        context.drawText(client.textRenderer, sortOrderText, textX, textY, 0xFFFFFF, true);
        this.sortOrderLeft = textX;
        this.sortOrderTop = textY - 1;
        this.sortOrderRight = textX + sortOrderTextW + 2;
        this.sortOrderBottom = textY + collapseTextH + 3;
        textY += 12;
        
        // 合成补充
        String craftRefillKey = config.craftRefill ? "portable_storage.toggle.enabled" : "portable_storage.toggle.disabled";
        Text craftRefillText = Text.translatable("portable_storage.ui.craft_refill", Text.translatable(craftRefillKey).getString());
        int craftRefillTextW = client.textRenderer.getWidth(craftRefillText);
        context.drawText(client.textRenderer, craftRefillText, textX, textY, 0xFFFFFF, true);
        this.craftRefillLeft = textX;
        this.craftRefillTop = textY - 1;
        this.craftRefillRight = textX + craftRefillTextW + 2;
        this.craftRefillBottom = textY + collapseTextH + 3;
        textY += 12;
        
        // 自动传入
        String autoDepositKey = config.autoDeposit ? "portable_storage.toggle.enabled" : "portable_storage.toggle.disabled";
        Text autoDepositText = Text.translatable("portable_storage.ui.auto_deposit", Text.translatable(autoDepositKey).getString());
        int autoDepositTextW = client.textRenderer.getWidth(autoDepositText);
        context.drawText(client.textRenderer, autoDepositText, textX, textY, 0xFFFFFF, true);
        this.autoDepositLeft = textX;
        this.autoDepositTop = textY - 1;
        this.autoDepositRight = textX + autoDepositTextW + 2;
        this.autoDepositBottom = textY + collapseTextH + 3;
        textY += 12;

        // 搜索位置
        String posKey;
        switch (config.searchPos) {
            case TOP: posKey = "portable_storage.search_pos.top"; break;
            case TOP2: posKey = "portable_storage.search_pos.top2"; break;
            case MIDDLE: posKey = "portable_storage.search_pos.middle"; break;
            default: posKey = "portable_storage.search_pos.bottom";
        }
        String posLabel = Text.translatable(posKey).getString();
        Text searchPosText = Text.translatable("portable_storage.ui.search_pos", posLabel);
        int searchPosTextW = client.textRenderer.getWidth(searchPosText);
        context.drawText(client.textRenderer, searchPosText, textX, textY, 0xFFFFFF, true);
        this.searchPosLeft = textX;
        this.searchPosTop = textY - 1;
        this.searchPosRight = textX + searchPosTextW + 2;
        this.searchPosBottom = textY + collapseTextH + 3;
        textY += 12;
        
        // 切换原版界面（仅在自定义工作台界面显示）
        if (switchToVanillaCallback != null) {
            Text switchVanillaText = Text.translatable("portable_storage.ui.switch_vanilla");
            int switchVanillaTextW = client.textRenderer.getWidth(switchVanillaText);
            context.drawText(client.textRenderer, switchVanillaText, textX, textY, 0xFFFFFF, true);
            this.switchVanillaLeft = textX;
            this.switchVanillaTop = textY - 1;
            this.switchVanillaRight = textX + switchVanillaTextW + 2;
            this.switchVanillaBottom = textY + collapseTextH + 3;
            textY += 12;
        }
    }

    private void drawSettingsBackground(DrawContext ctx, int x, int y, int w, int h) {
        // 右侧设置背景：64x109，边框4px（九宫格）
        final int texW = 64;
        final int texH = 109;
        final int border = 4;

        int srcLeft = border;
        int srcRight = texW - border;
        int srcTop = border;
        int srcBottom = texH - border;

        int dstMidW = Math.max(0, w - border * 2);
        int dstMidH = Math.max(0, h - border * 2);
        int srcMidW = texW - border * 2;
        int srcMidH = texH - border * 2;

        // 四角
        ctx.drawTexture(TEX_SETTINGS_BG, x, y, 0, 0, border, border, texW, texH);
        ctx.drawTexture(TEX_SETTINGS_BG, x + w - border, y, srcRight, 0, border, border, texW, texH);
        ctx.drawTexture(TEX_SETTINGS_BG, x, y + h - border, 0, srcBottom, border, border, texW, texH);
        ctx.drawTexture(TEX_SETTINGS_BG, x + w - border, y + h - border, srcRight, srcBottom, border, border, texW, texH);

        // 上下边
        if (dstMidW > 0) {
            drawRegionScaled(ctx, TEX_SETTINGS_BG, x + border, y, srcLeft, 0, srcMidW, border, dstMidW, border, texW, texH);
            drawRegionScaled(ctx, TEX_SETTINGS_BG, x + border, y + h - border, srcLeft, srcBottom, srcMidW, border, dstMidW, border, texW, texH);
        }
        // 左右边
        if (dstMidH > 0) {
            drawRegionScaled(ctx, TEX_SETTINGS_BG, x, y + border, 0, srcTop, border, srcMidH, border, dstMidH, texW, texH);
            drawRegionScaled(ctx, TEX_SETTINGS_BG, x + w - border, y + border, srcRight, srcTop, border, srcMidH, border, dstMidH, texW, texH);
        }
        // 中心
        if (dstMidW > 0 && dstMidH > 0) {
            drawRegionScaled(ctx, TEX_SETTINGS_BG, x + border, y + border, srcLeft, srcTop, srcMidW, srcMidH, dstMidW, dstMidH, texW, texH);
        }
    }
    
    /**
     * 更新搜索框位置
     */
    private void updateSearchFieldPosition() {
        if (this.searchField == null) return;
        ClientConfig cfg = ClientConfig.getInstance();
        int fieldWidth = cols * (slotSize + slotSpacing);
        int fieldX = gridLeft;
        int h = this.searchField.getHeight();
        int fieldY;
        switch (cfg.searchPos) {
            case TOP:
                // 玩家背包UI上方（紧贴）
                fieldY = baseY - h - 6;
                break;
            case TOP2:
                // 玩家背包UI上方（更靠上）
                fieldY = baseY - h - 6 - 18;
                break;
            case MIDDLE:
                // 背包与仓库之间
                fieldY = baseY + baseBgH + 2;
                break;
            default:
                // 底部（仓库下方）
                fieldY = gridTop + visibleRows * (slotSize + slotSpacing) + 6;
        }
        this.searchField.setWidth(fieldWidth);
        this.searchField.setX(fieldX);
        this.searchField.setY(fieldY);
    }
    
    // ========== 辅助方法 ==========
    
    private List<Integer> buildFilteredIndices() {
        List<Integer> result = new ArrayList<>();
        var stacks = ClientStorageState.getStacks();
        
        for (int i = 0; i < stacks.size(); i++) {
            var stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) continue;
            if (query.isEmpty() || matchesQuery(stack, query)) {
                result.add(i);
            }
        }
        
        sortIndices(result);
        return result;
    }
    
    private boolean matchesQuery(ItemStack stack, String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        if (lower.startsWith("@")) {
            String modId = Registries.ITEM.getId(stack.getItem()).getNamespace();
            return modId.contains(lower.substring(1));
        } else if (lower.startsWith("#")) {
            List<Text> tooltip = net.minecraft.client.gui.screen.Screen.getTooltipFromItem(MinecraftClient.getInstance(), stack);
            for (Text line : tooltip) {
                if (line.getString().toLowerCase(Locale.ROOT).contains(lower.substring(1))) {
                    return true;
                }
            }
            return false;
        } else {
            return stack.getName().getString().toLowerCase(Locale.ROOT).contains(lower);
        }
    }
    
    private void sortIndices(List<Integer> indices) {
        // 缓存已在render时重置，这里直接排序即可
        ClientConfig config = ClientConfig.getInstance();
        var stacks = ClientStorageState.getStacks();
        boolean ascending = config.sortAscending;
        
        java.util.Comparator<Integer> comparator = (a, b) -> {
            ItemStack stackA = stacks.get(a);
            ItemStack stackB = stacks.get(b);
            
            int cmp = switch (config.sortMode) {
                case COUNT -> Long.compare(ClientStorageState.getCount(a), ClientStorageState.getCount(b));
                case NAME -> {
                    String nameA = stackA.getName().getString();
                    String nameB = stackB.getName().getString();
                    yield nameA.compareToIgnoreCase(nameB);
                }
                case MOD_ID -> {
                    String modA = Registries.ITEM.getId(stackA.getItem()).getNamespace();
                    String modB = Registries.ITEM.getId(stackB.getItem()).getNamespace();
                    int modCmp = modA.compareToIgnoreCase(modB);
                    if (modCmp != 0) yield modCmp;
                    String nameA = stackA.getName().getString();
                    String nameB = stackB.getName().getString();
                    yield nameA.compareToIgnoreCase(nameB);
                }
                case UPDATE_TIME -> Long.compare(ClientStorageState.getTimestamp(a), ClientStorageState.getTimestamp(b));
            };
            
            return ascending ? cmp : -cmp;
        };
        
        indices.sort(comparator);
    }
    
    private String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format(Locale.US, "%.1fk", count / 1000.0);
        if (count < 1000000000) return String.format(Locale.US, "%.1fM", count / 1000000.0);
        return String.format(Locale.US, "%.1fG", count / 1000000000.0);
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "未知";

        // 将时间戳转换为本地时间
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());

        // 格式化显示：MM-dd HH:mm
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy MM-dd HH:mm");
        return dateTime.format(formatter);
    }
    
    private int calculatePanelWidth(MinecraftClient client, ClientConfig config, boolean enableCollapse) {
        int padding = 12;
        int maxWidth = 80;
        
        // 折叠仓库文本
        if (enableCollapse) {
            Text collapseText = Text.translatable("portable_storage.ui.collapse");
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(collapseText) + padding);
        }
        
        String sortModeKey = switch (config.sortMode) {
            case COUNT -> "portable_storage.sort.count";
            case NAME -> "portable_storage.sort.name";
            case MOD_ID -> "portable_storage.sort.mod_id";
            case UPDATE_TIME -> "portable_storage.sort.update_time";
        };
        Text sortModeText = Text.translatable("portable_storage.ui.sort_mode", Text.translatable(sortModeKey).getString());
        maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(sortModeText) + padding);
        
        String sortOrderKey = config.sortAscending ? "portable_storage.sort.ascending" : "portable_storage.sort.descending";
        Text sortOrderText = Text.translatable("portable_storage.ui.sort_order", Text.translatable(sortOrderKey).getString());
        maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(sortOrderText) + padding);
        
        String craftRefillKey = config.craftRefill ? "portable_storage.toggle.enabled" : "portable_storage.toggle.disabled";
        Text craftRefillText = Text.translatable("portable_storage.ui.craft_refill", Text.translatable(craftRefillKey).getString());
        maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(craftRefillText) + padding);
        
        String autoDepositKey = config.autoDeposit ? "portable_storage.toggle.enabled" : "portable_storage.toggle.disabled";
        Text autoDepositText = Text.translatable("portable_storage.ui.auto_deposit", Text.translatable(autoDepositKey).getString());
        maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(autoDepositText) + padding);
        
        // 切换原版界面文本宽度
        if (switchToVanillaCallback != null) {
            Text switchVanillaText = Text.translatable("portable_storage.ui.switch_vanilla");
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(switchVanillaText) + padding);
        }
        
        return maxWidth;
    }
    
    private void drawExtensionBackground(DrawContext ctx, int x, int y, int w, int h) {
        // 九宫格拉伸（通过矩阵缩放避免平铺）。
        final int texW = 75;
        final int texH = 122;
        final int border = 4; // 边框

        int srcLeft = border;
        int srcRight = texW - border;
        int srcTop = border;
        int srcBottom = texH - border;

        int dstMidW = Math.max(0, w - border * 2);
        int dstMidH = Math.max(0, h - border * 2);
        int srcMidW = texW - border * 2;
        int srcMidH = texH - border * 2;

        // 四角（不缩放）
        ctx.drawTexture(TEX_BG, x, y, 0, 0, border, border, texW, texH);
        ctx.drawTexture(TEX_BG, x + w - border, y, srcRight, 0, border, border, texW, texH);
        ctx.drawTexture(TEX_BG, x, y + h - border, 0, srcBottom, border, border, texW, texH);
        ctx.drawTexture(TEX_BG, x + w - border, y + h - border, srcRight, srcBottom, border, border, texW, texH);

        // 上下边（水平缩放）
        if (dstMidW > 0) {
            drawRegionScaled(ctx, TEX_BG, x + border, y, srcLeft, 0, srcMidW, border, dstMidW, border, texW, texH);
            drawRegionScaled(ctx, TEX_BG, x + border, y + h - border, srcLeft, srcBottom, srcMidW, border, dstMidW, border, texW, texH);
        }
        // 左右边（垂直缩放）
        if (dstMidH > 0) {
            drawRegionScaled(ctx, TEX_BG, x, y + border, 0, srcTop, border, srcMidH, border, dstMidH, texW, texH);
            drawRegionScaled(ctx, TEX_BG, x + w - border, y + border, srcRight, srcTop, border, srcMidH, border, dstMidH, texW, texH);
        }
        // 中心（双向缩放）
        if (dstMidW > 0 && dstMidH > 0) {
            drawRegionScaled(ctx, TEX_BG, x + border, y + border, srcLeft, srcTop, srcMidW, srcMidH, dstMidW, dstMidH, texW, texH);
        }
    }

    private void drawRegionScaled(DrawContext ctx, Identifier tex, int x, int y, int u, int v, int srcW, int srcH, int dstW, int dstH, int texW, int texH) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        float scaleX = (float)dstW / (float)srcW;
        float scaleY = (float)dstH / (float)srcH;
        ctx.getMatrices().scale(scaleX, scaleY, 1.0f);
        ctx.drawTexture(tex, 0, 0, u, v, srcW, srcH, texW, texH);
        ctx.getMatrices().pop();
    }
    
    private void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        // 使用背景纹理铺满面板区域
        drawExtensionBackground(ctx, x, y, w, h);
    }
    
    private void drawSlotInset(DrawContext ctx, int x, int y, int w, int h) {
        // 使用槽位纹理绘制（根据目标尺寸缩放）
        ctx.drawTexture(TEX_SLOT, x, y, 0, 0, w, h, 18, 18);
    }
    
    private void drawScrollbar(DrawContext ctx, int left, int top, int width, int height, int maxScrollRows) {
        // 轨道
        int track = 0x55000000;
        ctx.fill(left, top, left + width, top + height, track);

        // 滑块高度与可视比例相关
        int visible = this.visibleRows;
        int total = Math.max(visible, this.totalRows);
        float frac = (float)visible / (float)total;
        int thumbMin = 8;
        int thumbHeight = Math.max(thumbMin, (int)(height * frac));

        int maxScrollPx = height - thumbHeight;
        int thumbOffset = (maxScrollRows <= 0) ? 0 : (int)(this.scroll * maxScrollPx);
        int thumbTop = top + thumbOffset;

        int thumbBg = 0xFF999999;
        int thumbDark = 0xFF555555;
        int thumbLight = 0xFFFFFFFF;
        ctx.fill(left, thumbTop, left + width, thumbTop + thumbHeight, thumbBg);
        // 边框（内凹效果）
        ctx.fill(left, thumbTop, left + width, thumbTop + 1, thumbDark);
        ctx.fill(left + width - 1, thumbTop, left + width, thumbTop + thumbHeight, thumbDark);
        ctx.fill(left, thumbTop + thumbHeight - 1, left + width, thumbTop + thumbHeight, thumbLight);
        ctx.fill(left, thumbTop, left + 1, thumbTop + thumbHeight, thumbLight);
    }
    
    // ========== 事件处理 ==========
    
    /**
     * 处理鼠标点击（支持折叠功能）
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean enableCollapse) {
        // 折叠态：仅响应"展开仓库"小块
        if (enableCollapse && collapsed) {
            if (button == 0 && isIn(mouseX, mouseY, expandTabLeft, expandTabTop, expandTabRight, expandTabBottom)) {
                collapsed = false;
                ClientConfig.getInstance().collapsed = false;
                ClientConfig.save();
                if (this.searchField != null) this.searchField.setFocused(false);
                return true;
            }
            // 折叠时其余点击透传，不拦截
            return false;
        }
        
        // 搜索框点击
        if (searchField != null && searchField.mouseClicked(mouseX, mouseY, button)) {
            this.searchField.setFocused(true);
            return true;
        }
        
        // 升级槽位点击
        for (int i = 0; i < 5; i++) {
            if (isIn(mouseX, mouseY, upgradeSlotLefts[i], upgradeSlotTops[i], upgradeSlotRights[i], upgradeSlotBottoms[i])) {
                if (button == 1) { // 右键点击 - 切换禁用状态
                    ClientUpgradeState.toggleSlotDisabled(i);
                    // 发送禁用状态变更到服务器
                    ClientPlayNetworking.send(new UpgradeSlotClickC2SPayload(i, button));
                    return true;
                } else if (button == 0) { // 左键点击 - 发送到服务器
                    ClientPlayNetworking.send(new UpgradeSlotClickC2SPayload(i, button));
                    return true;
                }
            }
        }
        
        // 滚动条点击
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            int height = this.scrollbarHeight;
            int visible = this.visibleRows;
            int total = Math.max(visible, this.totalRows);
            int thumbMin = 8;
            int thumbHeight = Math.max(thumbMin, (int)(height * ((float)visible / (float)total)));
            int maxScrollPx = Math.max(1, height - thumbHeight);
            int top = this.scrollbarTop;
            
            int currentThumbTop = top + (int)((maxScrollPx) * this.scroll + 0.5f);
            boolean overThumb = mouseY >= currentThumbTop && mouseY < currentThumbTop + thumbHeight;
            if (overThumb) {
                dragScrollbar = true;
                dragGrabOffset = (int)mouseY - currentThumbTop;
            } else {
                int mouseOffset = (int)mouseY - top - thumbHeight / 2;
                float newScroll = (float)mouseOffset / (float)maxScrollPx;
                this.scroll = Math.max(0.0f, Math.min(1.0f, newScroll));
                dragScrollbar = true;
                dragGrabOffset = thumbHeight / 2;
                ClientPlayNetworking.send(new ScrollC2SPayload(this.scroll));
            }
            return true;
        }
        
        // 仓库槽位点击
        if (button == 0 || button == 1) {
            for (int row = 0; row < visibleRows; row++) {
                for (int col = 0; col < cols; col++) {
                    int sx = gridLeft + col * (slotSize + slotSpacing);
                    int sy = gridTop + row * (slotSize + slotSpacing);
                    if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                        int visIdx = row * cols + col;
                        if (visIdx < visibleIndexMap.length) {
                            int storageIndex = visibleIndexMap[visIdx];
                            if (storageIndex >= 0) {
                                ClientPlayNetworking.send(new StorageSlotClickC2SPayload(storageIndex, button));
                                return true;
                            } else {
                                // 空格子：若鼠标上有物品，则投递到仓库
                                var mc = MinecraftClient.getInstance();
                                if (mc != null && mc.player != null) {
                                    ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
                                    if (!cursor.isEmpty()) {
                                        ClientPlayNetworking.send(new DepositCursorC2SPayload(button));
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 设置点击
        ClientConfig config = ClientConfig.getInstance();
        if (button == 0) {
            // 折叠仓库（仅在enableCollapse为true时）
            if (enableCollapse && isIn(mouseX, mouseY, collapseLeft, collapseTop, collapseRight, collapseBottom)) {
                collapsed = true;
                config.collapsed = true;
                ClientConfig.save();
                if (this.searchField != null) this.searchField.setFocused(false);
                return true;
            }
            
            if (isIn(mouseX, mouseY, sortModeLeft, sortModeTop, sortModeRight, sortModeBottom)) {
                config.sortMode = ClientConfig.SortMode.values()[(config.sortMode.ordinal() + 1) % ClientConfig.SortMode.values().length];
                ClientConfig.save();
                return true;
            }
            if (isIn(mouseX, mouseY, sortOrderLeft, sortOrderTop, sortOrderRight, sortOrderBottom)) {
                config.sortAscending = !config.sortAscending;
                ClientConfig.save();
                return true;
            }
            if (isIn(mouseX, mouseY, craftRefillLeft, craftRefillTop, craftRefillRight, craftRefillBottom)) {
                config.craftRefill = !config.craftRefill;
                ClientConfig.save();
                return true;
            }
            if (isIn(mouseX, mouseY, autoDepositLeft, autoDepositTop, autoDepositRight, autoDepositBottom)) {
                config.autoDeposit = !config.autoDeposit;
                ClientConfig.save();
                return true;
            }
            // 搜索位置切换
            if (isIn(mouseX, mouseY, searchPosLeft, searchPosTop, searchPosRight, searchPosBottom)) {
                config.searchPos = config.searchPos.next();
                ClientConfig.save();
                return true;
            }
            // 切换原版界面
            if (switchToVanillaCallback != null && isIn(mouseX, mouseY, switchVanillaLeft, switchVanillaTop, switchVanillaRight, switchVanillaBottom)) {
                switchToVanillaCallback.run();
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 处理鼠标点击（不支持折叠）
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked(mouseX, mouseY, button, false);
    }
    
    /**
     * 处理鼠标滚轮
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isOverStorageArea(mouseX, mouseY)) {
            float delta = (float)verticalAmount * -0.1f;
            this.scroll = Math.max(0.0f, Math.min(1.0f, this.scroll + delta));
            ClientPlayNetworking.send(new ScrollC2SPayload(this.scroll));
            return true;
        }
        return false;
    }
    
    /**
     * 处理按键
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField != null && searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }
    
    public boolean charTyped(char chr, int modifiers) {
        if (searchField != null && searchField.charTyped(chr, modifiers)) {
            return true;
        }
        return false;
    }
    
    /**
     * 检查鼠标是否在仓库区域（用于 EMI 集成）
     */
    public boolean isMouseOverStorageArea(int mouseX, int mouseY) {
        int width = cols * (slotSize + slotSpacing);
        int height = visibleRows * (slotSize + slotSpacing);
        return mouseX >= gridLeft && mouseX < gridLeft + width && mouseY >= gridTop && mouseY < gridTop + height;
    }
    
    /**
     * 获取鼠标下的物品（用于 EMI 集成）
     */
    public ItemStack getItemUnderMouse(int mouseX, int mouseY) {
        if (!isMouseOverStorageArea(mouseX, mouseY)) {
            return ItemStack.EMPTY;
        }
        
        // 计算鼠标在网格中的位置
        int relativeX = mouseX - gridLeft;
        int relativeY = mouseY - gridTop;
        
        int col = relativeX / (slotSize + slotSpacing);
        int row = relativeY / (slotSize + slotSpacing);
        
        if (col >= 0 && col < cols && row >= 0 && row < visibleRows) {
            int index = row * cols + col;
            if (index < visibleIndexMap.length) {
                int actualIndex = visibleIndexMap[index];
                var storageStacks = ClientStorageState.getStacks();
                if (actualIndex >= 0 && actualIndex < storageStacks.size()) {
                    return storageStacks.get(actualIndex);
                }
            }
        }
        
        return ItemStack.EMPTY;
    }
    
    // ========== 辅助判断 ==========
    
    private boolean isIn(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }
    
    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= scrollbarLeft && mouseX < scrollbarLeft + scrollbarWidth && 
               mouseY >= scrollbarTop && mouseY < scrollbarTop + scrollbarHeight;
    }
    
    private boolean isOverStorageArea(double mouseX, double mouseY) {
        int width = cols * (slotSize + slotSpacing);
        int height = visibleRows * (slotSize + slotSpacing);
        return mouseX >= gridLeft && mouseX < gridLeft + width && mouseY >= gridTop && mouseY < gridTop + height;
    }
    
    public boolean isOverAnyComponent(double mouseX, double mouseY) {
        return isOverStorageArea(mouseX, mouseY) || isOverScrollbar(mouseX, mouseY) || isOverUpgradeArea(mouseX, mouseY);
    }
    
    private boolean isOverUpgradeArea(double mouseX, double mouseY) {
        for (int i = 0; i < upgradeCount; i++) {
            if (isIn(mouseX, mouseY, upgradeSlotLefts[i], upgradeSlotTops[i], upgradeSlotRights[i], upgradeSlotBottoms[i])) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查鼠标是否悬停在升级槽位上
     */
    private boolean portableStorage$isOverUpgradeSlot(double mouseX, double mouseY) {
        for (int i = 0; i < upgradeCount; i++) {
            if (isIn(mouseX, mouseY, upgradeSlotLefts[i], upgradeSlotTops[i], upgradeSlotRights[i], upgradeSlotBottoms[i])) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取悬停的升级槽位索引
     */
    private int portableStorage$getHoveredUpgradeSlot(double mouseX, double mouseY) {
        for (int i = 0; i < upgradeCount; i++) {
            if (isIn(mouseX, mouseY, upgradeSlotLefts[i], upgradeSlotTops[i], upgradeSlotRights[i], upgradeSlotBottoms[i])) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 获取升级名称
     */
    private String portableStorage$getUpgradeName(int slotIndex) {
        String key;
        switch (slotIndex) {
            case 0: key = "block.minecraft.crafting_table"; break;
            case 1: key = "block.minecraft.hopper"; break;
            case 2: key = "block.minecraft.chest"; break;
            case 3: key = "block.minecraft.barrel"; break;
            case 4: key = "block.minecraft.shulker_box"; break;
            default: key = "portable_storage.upgrade.unknown";
        }
        return Text.translatable(key).getString();
    }
    
    // Getters
    public TextFieldWidget getSearchField() {
        return searchField;
    }
    
    public int getGridLeft() { return gridLeft; }
    public int getGridTop() { return gridTop; }
    public int getGridWidth() { return cols * (slotSize + slotSpacing); }
    public int getGridHeight() { return visibleRows * (slotSize + slotSpacing); }
    public int getScrollbarLeft() { return scrollbarLeft; }
    public int getScrollbarTop() { return scrollbarTop; }
    public int getScrollbarWidth() { return scrollbarWidth; }
    public int getScrollbarHeight() { return scrollbarHeight; }
    
    public boolean isCollapsed() { return collapsed; }
    public void setCollapsed(boolean collapsed) { 
        this.collapsed = collapsed;
    }
    
    public void loadCollapsedState() {
        this.collapsed = ClientConfig.getInstance().collapsed;
    }
}

