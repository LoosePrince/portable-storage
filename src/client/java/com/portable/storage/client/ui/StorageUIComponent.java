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
    // 当前激活实例（用于全局滚轮转发）
    private static StorageUIComponent currentInstance;

    public static StorageUIComponent getCurrentInstance() {
        return currentInstance;
    }
    // 纹理
    private static final Identifier TEX_BG = Identifier.of("portable-storage", "textures/gui/portable_storage_gui.png");
    private static final Identifier TEX_SLOT = Identifier.of("portable-storage", "textures/gui/slot.png");
    private static final Identifier TEX_ICONS = Identifier.of("portable-storage", "textures/gui/icon.png");
    private static final Identifier TEX_DELETE = Identifier.of("portable-storage", "textures/gui/icon/delete.png");
    
    // 切换原版界面回调
    private Runnable switchToVanillaCallback = null;
    // 仓库网格参数
    private final int cols = 9;
    private int visibleRows = 6; // 改为可变，支持自适应
    private final int minVisibleRows = 2; // 最小行数，最大行数由配置决定
    private final int slotSize = 18;
    private final int slotSpacing = 0;
    
    // 升级槽位参数
    private final int upgradeSlotSize = 18;
    private final int upgradeSpacing = 0;
    private final int baseUpgradeCount = 5;
    private final int extendedUpgradeCount = 6;
    private final int totalUpgradeCount = baseUpgradeCount + extendedUpgradeCount;
    
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
    // 智能折叠：代表索引 -> 折叠信息
    private static final class CollapsedInfo {
        ItemStack displayStack; // 无 NBT + 光效 的展示用物品
        int totalCount;         // 折叠的总数量
        String itemId;          // 物品完整 id，用于右键展开
    }
    private java.util.Map<Integer, CollapsedInfo> collapsedInfoByRep = new java.util.HashMap<>();
    
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
    private final int[] upgradeSlotLefts = new int[11];
    private final int[] upgradeSlotTops = new int[11];
    private final int[] upgradeSlotRights = new int[11];
    private final int[] upgradeSlotBottoms = new int[11];
    
    // 升级槽位滚动相关
    private float upgradeScroll = 0.0f;
    private boolean dragUpgradeScrollbar = false;
    private int dragUpgradeGrabOffset = 0;
    private int upgradeScrollbarLeft, upgradeScrollbarTop, upgradeScrollbarHeight, upgradeScrollbarWidth;
    
    // 流体槽位点击区域
    private int fluidSlotLeft, fluidSlotTop, fluidSlotRight, fluidSlotBottom;
    
    // 设置面板点击区域
    private int sortModeLeft, sortModeTop, sortModeRight, sortModeBottom;
    private int sortOrderLeft, sortOrderTop, sortOrderRight, sortOrderBottom;
    private int craftRefillLeft, craftRefillTop, craftRefillRight, craftRefillBottom;
    private int autoDepositLeft, autoDepositTop, autoDepositRight, autoDepositBottom;
    private int searchPosLeft, searchPosTop, searchPosRight, searchPosBottom;
    private int storagePosLeft, storagePosTop, storagePosRight, storagePosBottom;
    private int smartCollapseLeft, smartCollapseTop, smartCollapseRight, smartCollapseBottom;
    
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
        // 记录当前实例，便于全局滚轮注入转发
        currentInstance = this;
        // 每次渲染前重置缓存，确保排序配置改变时能正确响应
        resetSortCache();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        
        // 检查仓库是否已启用
        if (!ClientStorageState.isStorageEnabled()) {
            return;
        }
        
        // 计算自适应高度
        ClientConfig config = ClientConfig.getInstance();
        int screenHeight = client.getWindow().getScaledHeight();
        int inventoryTop = screenY;
        int inventoryBottom = screenY + backgroundHeight;
        this.visibleRows = calculateAdaptiveHeight(screenHeight, inventoryTop, inventoryBottom, config);
        
        // 计算位置
        this.baseX = screenX;
        this.baseY = screenY;
        this.baseBgW = backgroundWidth;
        this.baseBgH = backgroundHeight;
        this.gridLeft = screenX + 8;
        int gapBelow = 6;
        int extraYOffset = 0;
        int searchH = (this.searchField != null ? this.searchField.getHeight() : 18);
        
        // 搜索位置为中间时的空间留白只在背包和工作台界面生效
        if (ClientConfig.getInstance().searchPos == ClientConfig.SearchPos.MIDDLE && portableStorage$isInventoryOrCraftingScreen()) {
            extraYOffset = searchH + 1; // 让仓库整体下移，给搜索框让位
        }
        
        // 根据仓库位置设置计算gridTop
        if (config.storagePos == ClientConfig.StoragePos.TOP) {
            // 仓库在顶部：计算仓库UI高度，然后让玩家背包下移
            int storageHeight = visibleRows * (slotSize + slotSpacing);
            this.gridTop = screenY - storageHeight - gapBelow - extraYOffset;
        } else {
            // 仓库在底部（默认）
            this.gridTop = screenY + backgroundHeight + gapBelow + extraYOffset;
        }
        
        // 折叠态：只显示一个"展开仓库"的小块
        if (enableCollapse && collapsed) {
            int tabW = 72;
            int tabH = 18;
            int tabLeft = screenX + (backgroundWidth - tabW) / 2; // 居中到背包下方
            int tabTop;
            if (config.storagePos == ClientConfig.StoragePos.TOP) {
                // 仓库在顶部时，折叠标签显示在背包上方
                tabTop = screenY - tabH - 6;
            } else {
                // 仓库在底部时，折叠标签显示在背包下方
                tabTop = screenY + backgroundHeight + 6;
            }
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
        
        // 计算扩展槽位需要的额外宽度
        int extendedSlotWidth = 0;
        if (ClientUpgradeState.isChestUpgradeActive()) {
            extendedSlotWidth = upgradeSlotSize + upgradeSpacing + 2; // 扩展槽位宽度 + 间距
        }
        
        int panelWidth = calculatePanelWidth(client, config, enableCollapse);
        int panelLeft = screenX + backgroundWidth + 8;
        
        int extLeft = upgradeLeft - 2 - extendedSlotWidth; // 向左扩展以包含扩展槽位
        int extTop = gridTop - 2;
        int extRight = panelLeft - 2; // 不包含设置面板区域
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
        
        // 渲染设置面板（独立显示，无背景）
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
        
        // 添加虚拟流体条目（在最前面显示）
        for (String fluidType : new String[]{"lava", "water", "milk"}) {
            int units = com.portable.storage.client.ClientUpgradeState.getFluidUnits(fluidType);
            if (units > 0) {
                // 为每种流体分配唯一的虚拟索引，使用固定的偏移量
                int fluidIndex = switch (fluidType) {
                    case "lava" -> Integer.MIN_VALUE + 1;
                    case "water" -> Integer.MIN_VALUE + 2;
                    case "milk" -> Integer.MIN_VALUE + 3;
                    default -> Integer.MIN_VALUE + 4;
                };
                // 检查是否匹配搜索条件
                if (query == null || query.isEmpty() || matchesVirtualFluidQuery(fluidType, query)) {
                    filtered.add(0, fluidIndex); // 在开头插入虚拟流体条目
                }
            }
        }
        
        // 如果启用附魔之瓶升级，则在虚拟流体后面插入一个虚拟条目用于显示"瓶装经验"
        boolean showXpBottle = com.portable.storage.client.ClientUpgradeState.isXpBottleUpgradeActive();
        if (showXpBottle) {
            // 检查是否匹配搜索条件
            if (query == null || query.isEmpty() || matchesXpBottleQuery(query)) {
                filtered.add(0, Integer.MIN_VALUE); // 在开头插入虚拟条目
            }
        }
        // 将收藏（星标）物品移动到最前（但在虚拟条目之后）
        // 虚拟条目使用 Integer.MIN_VALUE 及其附近的负值作为索引，真实物品索引为 >=0
        {
            java.util.Set<String> fav = com.portable.storage.client.ClientConfig.getInstance().favorites;
            if (fav != null && !fav.isEmpty()) {
                var stacks = com.portable.storage.client.ClientStorageState.getStacks();
                java.util.List<Integer> favs = new java.util.ArrayList<>();
                java.util.List<Integer> normals = new java.util.ArrayList<>();
                for (int idx : filtered) {
                    if (idx >= 0 && idx < stacks.size()) {
                        var st = stacks.get(idx);
                        if (st != null && !st.isEmpty()) {
                            String id = net.minecraft.registry.Registries.ITEM.getId(st.getItem()).toString();
                            if (fav.contains(id)) {
                                favs.add(idx);
                                continue;
                            }
                        }
                    }
                    normals.add(idx);
                }
                // 将虚拟条目（负值）保持在最前，其后插入收藏，再接其余
                java.util.List<Integer> reordered = new java.util.ArrayList<>(filtered.size());
                for (int v : filtered) if (v < 0) reordered.add(v);
                reordered.addAll(favs);
                // 保持 normals 中的负值不会重复加入（已在前面处理），仅加入非负或未分类的
                for (int v : normals) if (v >= 0) reordered.add(v); else if (!reordered.contains(v)) reordered.add(v);
                filtered = reordered;
            }
        }

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
            // 渲染槽位背景
            for (int col = 0; col < cols; col++) {
                int sx = gridLeft + col * (slotSize + slotSpacing);
                int sy = gridTop + row * (slotSize + slotSpacing);
                drawSlotInset(context, sx, sy, slotSize, slotSize);
                
                int filteredIndex = modelRow * cols + col;
                if (modelRow < totalRows && filteredIndex >= 0 && filteredIndex < filteredSize) {
                    int storageIndex = filtered.get(filteredIndex);
                    var stacks = ClientStorageState.getStacks();
                    if (storageIndex == Integer.MIN_VALUE) {
                        // 渲染虚拟“瓶装经验”
                        ItemStack display = new ItemStack(net.minecraft.item.Items.EXPERIENCE_BOTTLE);
                        context.getMatrices().push();
                        context.getMatrices().translate(0.0f, 0.0f, 100.0f);
                        context.drawItem(display, sx + 1, sy + 1);
                        // 渲染数量为“仓库XP池”的数值
                        long totalXp = com.portable.storage.client.ClientUpgradeState.getCachedXpPool();
                        if (totalXp > 0) {
                            String countText = formatCount((int)Math.min(Integer.MAX_VALUE, totalXp));
                            float scale = 0.75f;
                            int textWidth = client.textRenderer.getWidth(countText);
                            int txUnscaled = sx + slotSize - 1 - (int)(textWidth * scale);
                            int tyUnscaled = sy + slotSize - (int)(9 * scale);
                            context.getMatrices().push();
                            context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                            context.getMatrices().scale(scale, scale, 1.0f);
                            context.drawText(client.textRenderer, countText, (int)(txUnscaled / scale), (int)(tyUnscaled / scale), 0xFFFFFF, true);
                            context.getMatrices().pop();
                        }
                        context.getMatrices().pop();

                        if (hoveredStack.isEmpty() && mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                            // 使用占位堆叠标记悬停
                            hoveredStack = display;
                            hoveredIndex = Integer.MIN_VALUE;
                        }
                        visibleIndexMap[row * cols + col] = -2; // -2 表示虚拟XP条目
                    } else if (storageIndex < Integer.MIN_VALUE + 1000) {
                        // 渲染虚拟流体
                        String fluidType = getFluidTypeFromVirtualIndex(storageIndex);
                        if (fluidType != null) {
                            int units = com.portable.storage.client.ClientUpgradeState.getFluidUnits(fluidType);
                            if (units > 0) {
                                // 直接绘制自定义流体贴图
                                context.getMatrices().push();
                                context.getMatrices().translate(0.0f, 0.0f, 100.0f);
                                drawFluidTexture(context, fluidType, sx + 1, sy + 1);
                                // 渲染数量
                                String countText = formatCount(units);
                                float scale = 0.75f;
                                int textWidth = client.textRenderer.getWidth(countText);
                                int txUnscaled = sx + slotSize - 1 - (int)(textWidth * scale);
                                int tyUnscaled = sy + slotSize - (int)(9 * scale);
                                context.getMatrices().push();
                                context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                                context.getMatrices().scale(scale, scale, 1.0f);
                                context.drawText(client.textRenderer, countText, (int)(txUnscaled / scale), (int)(tyUnscaled / scale), 0xFFFFFF, true);
                                context.getMatrices().pop();
                                context.getMatrices().pop();
                                
                                if (hoveredStack.isEmpty() && mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                                    // 为虚拟流体创建悬停显示物品
                                    hoveredStack = createFluidDisplayStack(fluidType);
                                    hoveredIndex = storageIndex;
                                }
                                visibleIndexMap[row * cols + col] = storageIndex;
                            }
                        }
                    } else if (storageIndex >= 0 && storageIndex < stacks.size()) {
                        var stack = stacks.get(storageIndex);
                        if (stack != null && !stack.isEmpty()) {
                            // 渲染物品
                            context.getMatrices().push();
                            context.getMatrices().translate(0.0f, 0.0f, 100.0f);
                            CollapsedInfo cinfo = collapsedInfoByRep.get(storageIndex);
                            ItemStack toRender = (cinfo != null && cinfo.displayStack != null) ? cinfo.displayStack : stack;
                            // 收藏（星标）金色覆盖层：应在物品下方、槽位贴图上方 → 先绘制覆盖层，再绘制物品
                            {
                                java.util.Set<String> fav = com.portable.storage.client.ClientConfig.getInstance().favorites;
                                if (fav != null && !fav.isEmpty()) {
                                    String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
                                    if (fav.contains(id)) {
                                        context.getMatrices().push();
                                        context.getMatrices().translate(0.0f, 0.0f, 95.0f);
                                        context.fill(sx + 1, sy + 1, sx + slotSize - 1, sy + slotSize - 1, 0x4063C1ED);
                                        context.getMatrices().pop();
                                    }
                                }
                            }
                            context.drawItem(toRender, sx + 1, sy + 1);
                            // 使用覆盖层渲染耐久条，但抑制默认数量渲染（将计数临时设为1）
                            int originalCount = toRender.getCount();
                            ItemStack overlayStack = toRender.copy();
                            overlayStack.setCount(1);
                            context.drawItemInSlot(client.textRenderer, overlayStack, sx + 1, sy + 1);

                            // 智能折叠时叠加浅绿色半透明覆盖层
                            if (cinfo != null) {
                                context.getMatrices().push();
                                context.getMatrices().translate(0.0f, 0.0f, 150.0f);
                                context.fill(sx + 1, sy + 1, sx + slotSize - 1, sy + slotSize - 1, 0x4055FF55);
                                context.getMatrices().pop();
                            }

                            

                            // 数量渲染：0.75 缩放，右下角
                            long logicalCount = (cinfo != null) ? cinfo.totalCount : ClientStorageState.getCount(storageIndex);
                            int displayCount = (int)Math.min(Integer.MAX_VALUE, logicalCount > 0 ? logicalCount : originalCount);
                            if (displayCount > 1) {
                                String countText = formatCount(displayCount);
                                float scale = 0.75f;
                                int textWidth = client.textRenderer.getWidth(countText);
                                int txUnscaled = sx + slotSize - 1 - (int)(textWidth * scale);
                                int tyUnscaled = sy + slotSize - (int)(9 * scale);
                                context.getMatrices().push();
                                context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                                context.getMatrices().scale(scale, scale, 1.0f);
                                context.drawText(client.textRenderer, countText, (int)(txUnscaled / scale), (int)(tyUnscaled / scale), 0xFFFFFF, true);
                                context.getMatrices().pop();
                            }
                            context.getMatrices().pop();
                            
                            if (hoveredStack.isEmpty() && mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                                hoveredStack = stack;
                                hoveredIndex = storageIndex;
                            }
                            visibleIndexMap[row * cols + col] = storageIndex;
                        }
                    }
                }

                // 槽位悬停白色半透明遮罩（与原版一致的高亮感）
                if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                    context.getMatrices().push();
                    context.getMatrices().translate(0.0f, 0.0f, 240.0f);
                    context.fill(sx + 1, sy + 1, sx + slotSize - 1, sy + slotSize - 1, 0x80FFFFFF);
                    context.getMatrices().pop();
                }
            }
        }
        
        // 检查是否悬停在设置面板上
        if (isIn(mouseX, mouseY, collapseLeft, collapseTop, collapseRight, collapseBottom)) {
            // 折叠仓库悬停提示
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.collapse"));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, sortModeLeft, sortModeTop, sortModeRight, sortModeBottom)) {
            // 排序模式悬停提示
            ClientConfig config = ClientConfig.getInstance();
            String sortModeKey = switch (config.sortMode) {
                case COUNT -> "portable_storage.sort.count";
                case NAME -> "portable_storage.sort.name";
                case MOD_ID -> "portable_storage.sort.mod_id";
                case UPDATE_TIME -> "portable_storage.sort.update_time";
            };
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.sort_mode", Text.translatable(sortModeKey).getString()));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, sortOrderLeft, sortOrderTop, sortOrderRight, sortOrderBottom)) {
            // 排序顺序悬停提示
            ClientConfig config = ClientConfig.getInstance();
            String sortOrderKey = config.sortAscending ? "portable_storage.sort.ascending" : "portable_storage.sort.descending";
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.sort_order", Text.translatable(sortOrderKey).getString()));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, craftRefillLeft, craftRefillTop, craftRefillRight, craftRefillBottom)) {
            // 合成补充悬停提示
            ClientConfig config = ClientConfig.getInstance();
            String craftRefillKey = config.craftRefill ? "portable_storage.toggle.enabled" : "portable_storage.toggle.disabled";
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.craft_refill", Text.translatable(craftRefillKey).getString()));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, autoDepositLeft, autoDepositTop, autoDepositRight, autoDepositBottom)) {
            // 自动传入悬停提示
            ClientConfig config = ClientConfig.getInstance();
            String autoDepositKey = config.autoDeposit ? "portable_storage.toggle.enabled" : "portable_storage.toggle.disabled";
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.auto_deposit", Text.translatable(autoDepositKey).getString()));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, smartCollapseLeft, smartCollapseTop, smartCollapseRight, smartCollapseBottom)) {
            // 智能折叠悬停提示
            ClientConfig config = ClientConfig.getInstance();
            String smartCollapseKey = config.smartCollapse ? "portable_storage.toggle.enabled" : "portable_storage.toggle.disabled";
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.smart_collapse", Text.translatable(smartCollapseKey).getString()));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, searchPosLeft, searchPosTop, searchPosRight, searchPosBottom)) {
            // 搜索位置悬停提示
            ClientConfig config = ClientConfig.getInstance();
            String posKey;
            if (config.storagePos == ClientConfig.StoragePos.TOP && config.searchPos == ClientConfig.SearchPos.TOP2) {
                posKey = "portable_storage.search_pos.top";
            } else {
                switch (config.searchPos) {
                    case TOP: posKey = "portable_storage.search_pos.top"; break;
                    case TOP2: posKey = "portable_storage.search_pos.top2"; break;
                    case MIDDLE: posKey = "portable_storage.search_pos.middle"; break;
                    default: posKey = "portable_storage.search_pos.bottom";
                }
            }
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.search_pos", Text.translatable(posKey).getString()));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, storagePosLeft, storagePosTop, storagePosRight, storagePosBottom)) {
            // 仓库位置悬停提示
            ClientConfig config = ClientConfig.getInstance();
            String storagePosKey = config.storagePos == ClientConfig.StoragePos.TOP ? 
                "portable_storage.storage_pos.top" : "portable_storage.storage_pos.bottom";
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.storage_pos", Text.translatable(storagePosKey).getString()));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        } else if (isIn(mouseX, mouseY, switchVanillaLeft, switchVanillaTop, switchVanillaRight, switchVanillaBottom)) {
            // 切换原版悬停提示
            List<Text> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(Text.translatable("portable_storage.ui.switch_vanilla"));
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        }
        // 检查是否悬停在流体槽位上
        else if (isIn(mouseX, mouseY, fluidSlotLeft, fluidSlotTop, fluidSlotRight, fluidSlotBottom)) {
            ItemStack fluidStack = ClientUpgradeState.getFluidStack();
            List<Text> tooltipLines = new java.util.ArrayList<>();
            
            // 第一行：流体槽位
            Text fluidSlotLine = Text.translatable("portable_storage.ui.fluid_slot");
            tooltipLines.add(fluidSlotLine);
            
            // 第二行：流体名称
            if (!fluidStack.isEmpty()) {
                tooltipLines.add(Text.translatable("portable_storage.ui.fluid_name", fluidStack.getName().getString()));
            } else {
                tooltipLines.add(Text.translatable("portable_storage.ui.fluid_name", Text.translatable("portable_storage.ui.fluid_empty").getString()));
            }
            
            // 第三行：说明
            tooltipLines.add(Text.translatable("portable_storage.ui.fluid_desc"));
            
            context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
        }
        // 检查是否悬停在升级槽位上
        else if (portableStorage$isOverUpgradeSlot(mouseX, mouseY)) {
            int slotIndex = portableStorage$getHoveredUpgradeSlot(mouseX, mouseY);
            if (slotIndex >= 0) {
                ItemStack stack = ClientUpgradeState.getStack(slotIndex);
                String upgradeName = portableStorage$getUpgradeName(slotIndex);
                boolean isDisabled = ClientUpgradeState.isSlotDisabled(slotIndex);

                List<Text> tooltipLines = new java.util.ArrayList<>();
                boolean hasItem = !stack.isEmpty();
                
                if (slotIndex == 10) {
                    // 垃圾桶槽位：只显示功能描述，不显示槽位号和升级名称
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.trash_slot"));
                } else {
                    // 第一行：槽位 + 勾选/叉号
                    boolean ok = hasItem && !isDisabled;
                    String symbol = ok ? "[✓]" : "[✗]"; // ✓ / ✗
                    Text slotLine = Text.translatable("portable_storage.ui.upgrade_slot", slotIndex + 1).copy().append(symbol);
                    tooltipLines.add(slotLine);
                    // 第二行：升级名称
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_name", upgradeName));
                }

                // 附加升级说明（末尾追加）
                if (slotIndex != 10) { // 垃圾桶槽位已经在上面处理了
                    switch (slotIndex) {
                        case 0 -> tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.crafting_table"));
                        case 1 -> tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.hopper"));
                        case 2 -> tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.chest"));
                        case 3 -> tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.barrel"));
                        case 5 -> tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.spectral_arrow"));
                        case 6 -> tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.bed"));
                        case 7 -> tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_desc.experience_bottle"));
                    }
                }

                if (slotIndex != 10) { // 垃圾桶槽位不显示禁用状态
                    if (hasItem && isDisabled) {
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_disabled"));
                    }
                }
                
                // 添加右键提示：槽位6为床升级，右键睡觉；其他槽位右键切换禁用
                if (slotIndex == 6) {
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_right_click_bed"));
                } else if (slotIndex == 7) {
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_right_click_xp"));
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_middle_click_maintenance"));
                    // 添加当前存取等级信息
                    int currentStep = com.portable.storage.client.ClientUpgradeState.getXpTransferStep();
                    int[] steps = {1, 5, 10, 100};
                    int level = steps[currentStep];
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_current_step", level));
                } else if (slotIndex == 10) {
                    // 垃圾桶槽位：添加右键清空提示
                    tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_right_click_trash"));
                } else {
                    if (slotIndex == 0) {
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_right_click_custom_crafting"));
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_middle_click_virtual_crafting"));
                        // 添加虚拟合成状态信息
                        boolean virtualCraftingVisible = com.portable.storage.client.ClientConfig.getInstance().virtualCraftingVisible;
                        Text virtualCraftingStatus = virtualCraftingVisible ? 
                            Text.translatable("portable_storage.toggle.enabled") : 
                            Text.translatable("portable_storage.toggle.disabled");
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_virtual_crafting_status", virtualCraftingStatus));
                        // 添加警告信息
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_virtual_crafting_warning"));
                    } else {
                        tooltipLines.add(Text.translatable("portable_storage.ui.upgrade_right_click_hint"));
                    }
                }

                context.drawTooltip(client.textRenderer, tooltipLines, mouseX, mouseY);
            }
        }
        // 检查是否悬停在物品上
        else if (!hoveredStack.isEmpty()) {
            List<Text> lines;
            if (hoveredIndex == Integer.MIN_VALUE) {
                // 瓶装经验自定义悬停提示
                long totalXp = com.portable.storage.client.ClientUpgradeState.getCachedXpPool();
                int level = portableStorage$levelFromTotalXp((int)Math.min(Integer.MAX_VALUE, totalXp));
                lines = new java.util.ArrayList<>();
                lines.add(Text.translatable("portable_storage.exp_bottle.title"));
                lines.add(Text.translatable("portable_storage.exp_bottle.current", String.valueOf(totalXp)));
                lines.add(Text.translatable("portable_storage.exp_bottle.equivalent", String.valueOf(level)));
                
                // 添加交互声明
                lines.add(Text.empty()); // 空行分隔
                lines.add(Text.translatable("portable_storage.exp_bottle.interact.left_click"));
                lines.add(Text.translatable("portable_storage.exp_bottle.interact.right_click"));
                lines.add(Text.translatable("portable_storage.exp_bottle.interact.glass_bottle"));
            } else if (hoveredIndex < Integer.MIN_VALUE + 1000) {
                // 虚拟流体自定义悬停提示
                String fluidType = getFluidTypeFromVirtualIndex(hoveredIndex);
                if (fluidType != null) {
                    int units = com.portable.storage.client.ClientUpgradeState.getFluidUnits(fluidType);
                    lines = new java.util.ArrayList<>();
                    lines.add(Text.translatable("portable_storage.fluid." + fluidType + ".title"));
                    lines.add(Text.translatable("portable_storage.fluid.units", String.valueOf(units)));
                    lines.add(Text.translatable("portable_storage.fluid.desc"));
                } else {
                    lines = net.minecraft.client.gui.screen.Screen.getTooltipFromItem(client, hoveredStack);
                }
            } else {
                lines = net.minecraft.client.gui.screen.Screen.getTooltipFromItem(client, hoveredStack);
            }
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
        drawScrollbar(context, trackLeft, trackTop, trackWidth, trackHeight, maxScrollRows, visibleRows, totalRows, this.scroll);
    }
    
    /**
     * 更新滚动条拖动状态
     */
    private void updateScrollbarDrag(double mouseX, double mouseY) {
        // 处理升级槽位滚动条拖动
        if (dragUpgradeScrollbar) {
            MinecraftClient mc = MinecraftClient.getInstance();
            long window = mc != null && mc.getWindow() != null ? mc.getWindow().getHandle() : 0L;
            boolean leftDown = false;
            if (window != 0L) {
                leftDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
            if (!leftDown) {
                dragUpgradeScrollbar = false;
            } else {
                int height = this.upgradeScrollbarHeight;
                int upgradeVisibleRows = calculateUpgradeVisibleRows();
                
                // 计算每列的最大行数
                int leftColumnMaxRows = ClientUpgradeState.isChestUpgradeActive() ? extendedUpgradeCount : 0;
                int rightColumnMaxRows = baseUpgradeCount + 1;
                int maxRowsInAnyColumn = Math.max(leftColumnMaxRows, rightColumnMaxRows);
                
                int visible = upgradeVisibleRows;
                int total = Math.max(visible, maxRowsInAnyColumn);
                int thumbMin = 8;
                int thumbHeight = Math.max(thumbMin, (int)(height * ((float)visible / (float)total)));
                int maxScrollPx = Math.max(1, height - thumbHeight);
                int top = this.upgradeScrollbarTop;
                int mouseOffset = (int)mouseY - top - dragUpgradeGrabOffset;
                float newScroll = (float)mouseOffset / (float)maxScrollPx;
                this.upgradeScroll = Math.max(0.0f, Math.min(1.0f, newScroll));
            }
        }
        
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
        
        // 升级槽位是两列布局，每列最多6行
        // 左列：扩展升级槽位（5-10，6个槽位）
        // 右列：基础升级槽位（0-4，5个槽位）+ 流体槽位（1个槽位）
        
        int upgradeVisibleRows = calculateUpgradeVisibleRows();
        
        // 计算每列的最大行数
        int leftColumnMaxRows = ClientUpgradeState.isChestUpgradeActive() ? extendedUpgradeCount : 0; // 扩展槽位行数
        int rightColumnMaxRows = baseUpgradeCount + 1; // 基础槽位 + 流体槽位行数
        
        // 计算滚动范围 - 基于最大列的行数
        int maxRowsInAnyColumn = Math.max(leftColumnMaxRows, rightColumnMaxRows);
        int maxUpgradeScrollRows = Math.max(0, maxRowsInAnyColumn - upgradeVisibleRows);
        
        // 如果没有滚动内容，重置滚动位置
        if (maxUpgradeScrollRows <= 0) {
            this.upgradeScroll = 0.0f;
        }
        
        int upgradeRowOffset = (int)Math.floor(upgradeScroll * maxUpgradeScrollRows + 0.5f);
        upgradeRowOffset = Math.max(0, Math.min(maxUpgradeScrollRows, upgradeRowOffset));
        
        // 为了兼容性，保留 maxVisibleUpgradeSlots 变量
        int maxVisibleUpgradeSlots = upgradeVisibleRows;
        
        // 渲染扩展升级槽位（5-10），仅在箱子升级激活时显示，显示在左侧
        if (ClientUpgradeState.isChestUpgradeActive()) {
            int extendedLeft = upgradeLeft - (upgradeSlotSize + upgradeSpacing + 2); // 左侧留一些间距
            
            for (int i = 0; i < extendedUpgradeCount; i++) {
                int slotIndex = baseUpgradeCount + i; // 槽位5-10
                int displayRow = i - upgradeRowOffset;
                if (displayRow < 0 || displayRow >= maxVisibleUpgradeSlots) continue; // 跳过不可见的槽位
                
                int sx = extendedLeft;
                int sy = upgradeTop + displayRow * (upgradeSlotSize + upgradeSpacing);
                drawSlotInset(context, sx, sy, upgradeSlotSize, upgradeSlotSize);
                
                upgradeSlotLefts[slotIndex] = sx;
                upgradeSlotTops[slotIndex] = sy;
                upgradeSlotRights[slotIndex] = sx + upgradeSlotSize;
                upgradeSlotBottoms[slotIndex] = sy + upgradeSlotSize;
                
                ItemStack upgradeStack = ClientUpgradeState.getStack(slotIndex);
                ItemStack expectedStack = com.portable.storage.storage.UpgradeInventory.getExpectedUpgradeForSlot(slotIndex);
                
                if (!upgradeStack.isEmpty()) {
                    // 有物品，正常渲染
                    context.drawItem(upgradeStack, sx + 1, sy + 1);
                    
                    // 如果槽位被禁用，添加红色半透明遮罩
                    if (ClientUpgradeState.isSlotDisabled(slotIndex)) {
                        context.getMatrices().push();
                        context.getMatrices().translate(0, 0, 200); // 提高层级
                        context.fill(sx + 1, sy + 1, sx + upgradeSlotSize - 1, sy + upgradeSlotSize - 1, 0x80FF0000);
                        context.getMatrices().pop();
                    }
                } else {
                    // 空槽位，显示预期物品图标并叠加白色半透明遮罩
                    if (slotIndex == 10) {
                        // 垃圾桶槽位：使用自定义删除图标
                        context.getMatrices().push();
                        context.getMatrices().translate(0, 0, 100);
                        context.drawTexture(TEX_DELETE, sx + 1, sy + 1, 0, 0, upgradeSlotSize - 2, upgradeSlotSize - 2, upgradeSlotSize - 2, upgradeSlotSize - 2);
                        context.getMatrices().pop();
                    } else {
                        context.drawItem(expectedStack, sx + 1, sy + 1);
                    }
                    
                    // 叠加白色半透明遮罩（在物品图标上方）
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, 200); // 提高层级
                    context.fill(sx + 1, sy + 1, sx + upgradeSlotSize - 1, sy + upgradeSlotSize - 1, 0x80FFFFFF);
                    context.getMatrices().pop();
                }
            }
        }
        
        // 渲染基础升级槽位（0-4）
        for (int i = 0; i < baseUpgradeCount; i++) {
            int displayRow = i - upgradeRowOffset;
            if (displayRow < 0 || displayRow >= maxVisibleUpgradeSlots) continue; // 跳过不可见的槽位
            
            int sx = upgradeLeft;
            int sy = upgradeTop + displayRow * (upgradeSlotSize + upgradeSpacing);
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
        
        // 渲染流体槽位（在升级槽位5下面）
        int fluidDisplayRow = 5 - upgradeRowOffset;
        if (fluidDisplayRow >= 0 && fluidDisplayRow < maxVisibleUpgradeSlots) {
            int fluidSlotY = upgradeTop + fluidDisplayRow * (upgradeSlotSize + upgradeSpacing);
            drawSlotInset(context, upgradeLeft, fluidSlotY, upgradeSlotSize, upgradeSlotSize);
            
            fluidSlotLeft = upgradeLeft;
            fluidSlotTop = fluidSlotY;
            fluidSlotRight = upgradeLeft + upgradeSlotSize;
            fluidSlotBottom = fluidSlotY + upgradeSlotSize;
            
            ItemStack fluidStack = ClientUpgradeState.getFluidStack();
            ItemStack expectedFluidStack = com.portable.storage.storage.UpgradeInventory.getExpectedFluidForSlot();
            
            if (!fluidStack.isEmpty()) {
                // 有物品，正常渲染
                context.drawItem(fluidStack, upgradeLeft + 1, fluidSlotY + 1);
            } else {
                // 空槽位，显示预期物品图标并叠加白色半透明遮罩
                context.drawItem(expectedFluidStack, upgradeLeft + 1, fluidSlotY + 1);
                
                // 叠加白色半透明遮罩（在物品图标上方）
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200); // 提高层级
                context.fill(upgradeLeft + 1, fluidSlotY + 1, upgradeLeft + upgradeSlotSize - 1, fluidSlotY + upgradeSlotSize - 1, 0x80FFFFFF);
                context.getMatrices().pop();
            }
        }
        
        // 渲染升级槽位滚动条（如果需要）
        if (maxUpgradeScrollRows > 0) {
            renderUpgradeScrollbar(context, upgradeLeft, upgradeTop, upgradeVisibleRows, maxRowsInAnyColumn, upgradeVisibleRows);
        }
        
        // 升级说明按钮已移除
    }
    
    /**
     * 渲染升级槽位滚动条
     */
    private void renderUpgradeScrollbar(DrawContext context, int upgradeLeft, int upgradeTop, int visibleRows, int totalRows, int upgradeVisibleRows) {
        // 滚动条位于升级槽位右侧（仓库槽位左侧），与仓库槽位滚动条对称
        int trackLeft = upgradeLeft + upgradeSlotSize + 4; // 在升级槽位右侧
        int trackTop = upgradeTop;
        int trackHeight = upgradeVisibleRows * (upgradeSlotSize + upgradeSpacing);
        int trackWidth = 6;
        
        this.upgradeScrollbarLeft = trackLeft;
        this.upgradeScrollbarTop = trackTop;
        this.upgradeScrollbarHeight = trackHeight;
        this.upgradeScrollbarWidth = trackWidth;
        
        int maxUpgradeScrollRows = Math.max(0, totalRows - visibleRows);
        
        // 使用通用滚动条绘制方法
        drawScrollbar(context, trackLeft, trackTop, trackWidth, trackHeight, maxUpgradeScrollRows, visibleRows, totalRows, this.upgradeScroll);
    }
    
    /**
     * 渲染设置面板
     */
    private void renderSettingsPanel(DrawContext context, MinecraftClient client, ClientConfig config, int panelLeft, int panelWidth, boolean enableCollapse) {
        int panelTop = gridTop - 6; // 对齐仓库界面顶部
        int panelBottom = gridTop + visibleRows * (slotSize + slotSpacing) + 8;
        // 不再绘制背景，因为每个图标都自带背景
        
        final int iconSize = 16;
        final int iconSpacing = 15; // 图标间距
        
        final int columnWidth = iconSize + 1; // 列宽度（图标宽度 + 间距）
        
        // 使用数组来存储位置信息，这样内部类可以访问
        final int[] position = {0, 0, panelLeft + 2, panelTop}; // [currentColumn, currentRow, iconX, iconY]
        
        // 辅助方法：计算下一个图标位置
        class IconPositionCalculator {
            void nextPosition() {
                position[1]++; // currentRow++
                
                // 计算下一个图标的位置
                int nextIconY = panelTop + position[1] * iconSpacing;
                int nextIconBottom = nextIconY + iconSize;
                
                // 检查下一个图标是否会超出可用高度
                if (nextIconBottom > panelBottom) {
                    position[1] = 0; // currentRow = 0
                    position[0]++; // currentColumn++
                    position[2] = panelLeft + 2 + position[0] * columnWidth; // iconX
                    position[3] = panelTop; // iconY 重置到顶部
                } else {
                    position[3] = nextIconY; // iconY
                }
            }
        }
        IconPositionCalculator positionCalc = new IconPositionCalculator();
        
        // 折叠仓库（仅在背包界面显示）
        if (enableCollapse) {
            drawIcon(context, 1, 1, position[2], position[3]);
            this.collapseLeft = position[2];
            this.collapseTop = position[3];
            this.collapseRight = position[2] + iconSize;
            this.collapseBottom = position[3] + iconSize;
            positionCalc.nextPosition();
        }
        
        // 排序模式
        int sortModeRow = 1, sortModeCol = 2; // 默认值
        switch (config.sortMode) {
            case COUNT -> { sortModeRow = 1; sortModeCol = 2; }
            case NAME -> { sortModeRow = 1; sortModeCol = 3; }
            case MOD_ID -> { sortModeRow = 1; sortModeCol = 4; }
            case UPDATE_TIME -> { sortModeRow = 1; sortModeCol = 5; }
        }
        drawIcon(context, sortModeRow, sortModeCol, position[2], position[3]);
        this.sortModeLeft = position[2];
        this.sortModeTop = position[3];
        this.sortModeRight = position[2] + iconSize;
        this.sortModeBottom = position[3] + iconSize;
        positionCalc.nextPosition();
        
        // 排序顺序
        int sortOrderRow = 2, sortOrderCol = config.sortAscending ? 1 : 2;
        drawIcon(context, sortOrderRow, sortOrderCol, position[2], position[3]);
        this.sortOrderLeft = position[2];
        this.sortOrderTop = position[3];
        this.sortOrderRight = position[2] + iconSize;
        this.sortOrderBottom = position[3] + iconSize;
        positionCalc.nextPosition();
        
        // 合成补充
        int craftRefillRow = 2, craftRefillCol = config.craftRefill ? 3 : 4;
        drawIcon(context, craftRefillRow, craftRefillCol, position[2], position[3]);
        this.craftRefillLeft = position[2];
        this.craftRefillTop = position[3];
        this.craftRefillRight = position[2] + iconSize;
        this.craftRefillBottom = position[3] + iconSize;
        positionCalc.nextPosition();
        
        // 自动传入
        int autoDepositRow = 2, autoDepositCol = config.autoDeposit ? 5 : 5; // 启用和禁用都用同一个图标位置
        drawIcon(context, autoDepositRow, autoDepositCol, position[2], position[3]);
        this.autoDepositLeft = position[2];
        this.autoDepositTop = position[3];
        this.autoDepositRight = position[2] + iconSize;
        this.autoDepositBottom = position[3] + iconSize;
        positionCalc.nextPosition();

        // 智能折叠开关
        int smartCollapseRow = 3, smartCollapseCol = config.smartCollapse ? 1 : 2;
        drawIcon(context, smartCollapseRow, smartCollapseCol, position[2], position[3]);
        this.smartCollapseLeft = position[2];
        this.smartCollapseTop = position[3];
        this.smartCollapseRight = position[2] + iconSize;
        this.smartCollapseBottom = position[3] + iconSize;
        positionCalc.nextPosition();

        // 搜索位置设置（只在背包和工作台界面显示）
        if (portableStorage$isInventoryOrCraftingScreen()) {
            int searchPosRow = 3, searchPosCol;
            switch (config.searchPos) {
                case BOTTOM -> searchPosCol = 3;
                case TOP -> searchPosCol = 3;
                case TOP2 -> searchPosCol = 3;
                case MIDDLE -> searchPosCol = 3;
                default -> searchPosCol = 3;
            }
            drawIcon(context, searchPosRow, searchPosCol, position[2], position[3]);
            this.searchPosLeft = position[2];
            this.searchPosTop = position[3];
            this.searchPosRight = position[2] + iconSize;
            this.searchPosBottom = position[3] + iconSize;
            positionCalc.nextPosition();
        } else {
            // 非背包/工作台界面时，搜索位置设置不可见
            this.searchPosLeft = 0;
            this.searchPosTop = 0;
            this.searchPosRight = 0;
            this.searchPosBottom = 0;
        }
        
        // 仓库位置
        int storagePosRow = 3, storagePosCol = config.storagePos == ClientConfig.StoragePos.TOP ? 4 : 4;
        drawIcon(context, storagePosRow, storagePosCol, position[2], position[3]);
        this.storagePosLeft = position[2];
        this.storagePosTop = position[3];
        this.storagePosRight = position[2] + iconSize;
        this.storagePosBottom = position[3] + iconSize;
        positionCalc.nextPosition();
        
        // 切换原版界面（仅在自定义工作台界面显示）
        if (switchToVanillaCallback != null) {
            drawIcon(context, 3, 5, position[2], position[3]);
            this.switchVanillaLeft = position[2];
            this.switchVanillaTop = position[3];
            this.switchVanillaRight = position[2] + iconSize;
            this.switchVanillaBottom = position[3] + iconSize;
            positionCalc.nextPosition();
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
        
        if (cfg.storagePos == ClientConfig.StoragePos.TOP) {
            // 仓库在顶部时的搜索框位置
            switch (cfg.searchPos) {
                case TOP:
                    // 仓库界面的上方
                    fieldY = gridTop - h - 6;
                    break;
                case MIDDLE:
                    // 仓库和玩家背包界面中间，即背包UI上面
                    fieldY = baseY - h;
                    break;
                case BOTTOM:
                default:
                    // 玩家背包界面底部
                    fieldY = baseY + baseBgH + 6;
                    break;
                case TOP2:
                    // 仓库在顶部时，TOP2等同于TOP
                    fieldY = gridTop - h - 6;
                    break;
            }
        } else {
            // 仓库在底部时的搜索框位置（原有逻辑）
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
                    fieldY = baseY + baseBgH + 1;
                    break;
                default:
                    // 底部（仓库下方）
                    fieldY = gridTop + visibleRows * (slotSize + slotSpacing) + 6;
            }
        }
        
        this.searchField.setWidth(fieldWidth);
        this.searchField.setX(fieldX);
        this.searchField.setY(fieldY);
    }
    
    // ========== 辅助方法 ==========
    
    private List<Integer> buildFilteredIndices() {
        this.collapsedInfoByRep.clear();
        List<Integer> result = new ArrayList<>();
        var stacks = ClientStorageState.getStacks();
        boolean smartCollapse = ClientConfig.getInstance().smartCollapse;
        boolean expandForSearch = query != null && !query.isEmpty();

        if (smartCollapse && !expandForSearch) {
            // 分组：对同一物品ID的不同 NBT 变体进行折叠（仅当同ID索引数量≥2）
            java.util.Map<String, java.util.List<Integer>> groups = new java.util.HashMap<>();
            for (int i = 0; i < stacks.size(); i++) {
                var stack = stacks.get(i);
                if (stack == null || stack.isEmpty()) continue;
                if (!(query.isEmpty() || matchesQuery(stack, query))) continue;
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                groups.computeIfAbsent(id, k -> new java.util.ArrayList<>()).add(i);
            }
            // 输出分组：每组选第一个作为代表，并记录折叠信息
            for (var entry : groups.entrySet()) {
                java.util.List<Integer> indices = entry.getValue();
                if (indices.isEmpty()) continue;
                if (indices.size() == 1) {
                    // 仅一个变体，不折叠
                    result.add(indices.get(0));
                    continue;
                }
                int rep = indices.get(0);
                long total = 0;
                for (int idx : indices) {
                    total += ClientStorageState.getCount(idx);
                }
                // 仅当折叠总数>1时折叠
                if (total <= 1) {
                    result.add(rep);
                    continue;
                }
                // 展示堆叠用无 NBT 的基础物品 + 光效
                ItemStack repStack = stacks.get(rep);
                ItemStack display = new ItemStack(repStack.getItem());
                display.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                CollapsedInfo info = new CollapsedInfo();
                info.displayStack = display;
                info.totalCount = (int)Math.min(Integer.MAX_VALUE, total);
                info.itemId = entry.getKey();
                collapsedInfoByRep.put(rep, info);
                result.add(rep);
            }
        } else {
            // 不折叠或 id: 搜索 → 逐条加入，支持按照 id 过滤
            for (int i = 0; i < stacks.size(); i++) {
                var stack = stacks.get(i);
                if (stack == null || stack.isEmpty()) continue;
                if (query.isEmpty() || matchesQuery(stack, query)) {
                    result.add(i);
                }
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
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            String fullId = Registries.ITEM.getId(stack.getItem()).toString();
            return name.contains(lower) || fullId.contains(lower);
        }
    }
    
    /**
     * 检查虚拟流体是否匹配搜索条件
     */
    private boolean matchesVirtualFluidQuery(String fluidType, String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        
        // 获取流体的翻译文本
        String fluidName = Text.translatable("portable_storage.fluid." + fluidType + ".title").getString().toLowerCase(Locale.ROOT);
        
        // 检查流体名称是否匹配
        if (fluidName.contains(lower)) {
            return true;
        }
        
        // 检查通用流体相关关键词
        String fluidDesc = Text.translatable("portable_storage.fluid.desc").getString().toLowerCase(Locale.ROOT);
        if (fluidDesc.contains(lower)) {
            return true;
        }
        
        // 检查英文流体名称（作为备用）
        String fluidNameEn = switch (fluidType) {
            case "lava" -> "lava";
            case "water" -> "water";
            case "milk" -> "milk";
            default -> fluidType;
        };
        
        return fluidNameEn.toLowerCase(Locale.ROOT).contains(lower);
    }
    
    /**
     * 检查瓶装经验是否匹配搜索条件
     */
    private boolean matchesXpBottleQuery(String q) {
        String lower = q.toLowerCase(Locale.ROOT);
        
        // 获取瓶装经验的翻译文本
        String xpBottleTitle = Text.translatable("portable_storage.exp_bottle.title").getString().toLowerCase(Locale.ROOT);
        String xpBottleCurrent = Text.translatable("portable_storage.exp_bottle.current", "").getString().toLowerCase(Locale.ROOT);
        String xpBottleEquivalent = Text.translatable("portable_storage.exp_bottle.equivalent", "").getString().toLowerCase(Locale.ROOT);
        
        // 检查翻译文本是否匹配
        if (xpBottleTitle.contains(lower) || 
            xpBottleCurrent.contains(lower) || 
            xpBottleEquivalent.contains(lower)) {
            return true;
        }
        
        // 检查通用经验相关关键词（作为备用）
        return "经验".toLowerCase(Locale.ROOT).contains(lower) ||
               "experience".toLowerCase(Locale.ROOT).contains(lower) ||
               "xp".toLowerCase(Locale.ROOT).contains(lower);
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
        if (count < 1000000) {
            double v = count / 1000.0;
            return v < 10 ? String.format(Locale.US, "%.1fk", v) : String.format(Locale.US, "%.0fk", v);
        }
        if (count < 1000000000) {
            double v = count / 1000000.0;
            return v < 10 ? String.format(Locale.US, "%.1fM", v) : String.format(Locale.US, "%.0fM", v);
        }
        double v = count / 1000000000.0;
        return v < 10 ? String.format(Locale.US, "%.1fG", v) : String.format(Locale.US, "%.0fG", v);
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



    private int portableStorage$xpToNextLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    private int portableStorage$levelFromTotalXp(int total) {
        total = Math.max(0, total);
        int lvl = 0;
        int remain = total;
        while (true) {
            int need = portableStorage$xpToNextLevel(lvl);
            if (remain < need) return lvl;
            remain -= need;
            lvl++;
            if (lvl > 10000) return lvl; // 安全上限
        }
    }
    
    private int calculatePanelWidth(MinecraftClient client, ClientConfig config, boolean enableCollapse) {
        // 计算设置项数量
        int settingCount = 0;
        if (enableCollapse) settingCount++; // 折叠仓库
        settingCount += 4; // 排序模式、排序顺序、合成补充、自动传入
        settingCount += 1; // 智能折叠
        if (portableStorage$isInventoryOrCraftingScreen()) settingCount += 1; // 搜索位置
        settingCount += 1; // 仓库位置
        if (switchToVanillaCallback != null) settingCount += 1; // 切换原版
        
        // 计算设置图标区域的实际可用高度
        int panelTop = gridTop - 6;
        int panelBottom = gridTop + visibleRows * (slotSize + slotSpacing) + 8;
        
        // 模拟图标布局来计算实际需要的列数
        final int iconSpacing = 15;
        final int iconSize = 16;
        
        int currentColumn = 0;
        int currentRow = 0;
        
        for (int i = 0; i < settingCount; i++) {
            // 计算下一个图标的位置
            int nextIconY = panelTop + currentRow * iconSpacing;
            int nextIconBottom = nextIconY + iconSize;
            
            // 检查下一个图标是否会超出可用高度
            if (nextIconBottom > panelBottom) {
                currentRow = 0;
                currentColumn++;
            } else {
                currentRow++;
            }
        }
        
        int columns = currentColumn + 1; // 列数从0开始，所以+1
        
        // 计算总宽度
        final int columnWidth = iconSize + 2; // 图标宽度 + 间距
        final int padding = 4; // 左右边距
        
        return columns * columnWidth + padding;
    }
    
    /**
     * 计算自适应的高度
     * @param screenHeight 屏幕高度
     * @param inventoryTop 背包界面顶部位置
     * @param inventoryBottom 背包界面底部位置
     * @param config 客户端配置
     * @return 计算出的可见行数
     */
    private int calculateAdaptiveHeight(int screenHeight, int inventoryTop, int inventoryBottom, ClientConfig config) {
        int availableHeight;
        
        if (config.storagePos == ClientConfig.StoragePos.TOP) {
            // 仓库在顶部：从屏幕顶部到背包界面顶部的空间
            availableHeight = inventoryTop - 20; // 留20px边距
        } else {
            // 仓库在底部：从背包界面底部到屏幕底部的空间
            availableHeight = screenHeight - inventoryBottom - 20; // 留20px边距
        }
        
        // 计算可以容纳的行数
        int slotHeight = slotSize + slotSpacing;
        int maxRows = Math.max(minVisibleRows, availableHeight / slotHeight);
        
        // 限制在最小和最大行数之间
        int configMaxRows = config.maxVisibleRows;
        return Math.min(configMaxRows, Math.max(minVisibleRows, maxRows));
    }
    
    /**
     * 计算升级槽位的可见行数
     */
    private int calculateUpgradeVisibleRows() {
        // 升级槽位使用与仓库相同的可见行数，但可以独立调整
        // 这里可以根据需要实现独立的升级槽位高度计算逻辑
        return visibleRows;
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
    
    /**
     * 从图标表中绘制指定位置的16x16图标
     * @param context 绘制上下文
     * @param row 行号 (1-3)
     * @param col 列号 (1-5)
     * @param x 绘制位置X
     * @param y 绘制位置Y
     */
    private void drawIcon(DrawContext context, int row, int col, int x, int y) {
        // 图标表是3行5列，每个图标16x16像素
        final int iconSize = 16;
        final int cols = 5;
        final int rows = 3;
        
        // 计算源纹理中的位置
        int srcX = (col - 1) * iconSize;
        int srcY = (row - 1) * iconSize;
        
        // 绘制16x16的图标
        context.drawTexture(TEX_ICONS, x, y, srcX, srcY, iconSize, iconSize, cols * iconSize, rows * iconSize);
    }
    
    private void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        // 使用背景纹理铺满面板区域
        drawExtensionBackground(ctx, x, y, w, h);
    }
    
    private void drawSlotInset(DrawContext ctx, int x, int y, int w, int h) {
        // 使用槽位纹理绘制（根据目标尺寸缩放）
        ctx.drawTexture(TEX_SLOT, x, y, 0, 0, w, h, 18, 18);
    }
    
    /**
     * 通用滚动条绘制函数
     * @param ctx 绘制上下文
     * @param left 滚动条左边界
     * @param top 滚动条顶部边界
     * @param width 滚动条宽度
     * @param height 滚动条高度
     * @param maxScrollRows 最大滚动行数
     * @param visibleSlots 可见槽位数
     * @param totalSlots 总槽位数
     * @param scroll 当前滚动位置 (0.0-1.0)
     */
    private void drawScrollbar(DrawContext ctx, int left, int top, int width, int height, int maxScrollRows, int visibleSlots, int totalSlots, float scroll) {
        // 轨道
        int track = 0x55000000;
        ctx.fill(left, top, left + width, top + height, track);

        // 滑块高度与可视比例相关
        int visible = visibleSlots;
        int total = Math.max(visible, totalSlots);
        float frac = (float)visible / (float)total;
        int thumbMin = 8;
        int thumbHeight = Math.max(thumbMin, (int)(height * frac));

        int maxScrollPx = height - thumbHeight;
        int thumbOffset = (maxScrollRows <= 0) ? 0 : (int)(scroll * maxScrollPx);
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
        
        // 流体槽位点击
        if (isIn(mouseX, mouseY, fluidSlotLeft, fluidSlotTop, fluidSlotRight, fluidSlotBottom)) {
            // 发送流体槽位点击到服务器
            ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                com.portable.storage.net.payload.StorageActionC2SPayload.Target.FLUID,
                0,
                button,
                0,
                "",
                0
            ));
            return true;
        }
        
        // 升级槽位点击
        for (int i = 0; i < totalUpgradeCount; i++) {
            if (isIn(mouseX, mouseY, upgradeSlotLefts[i], upgradeSlotTops[i], upgradeSlotRights[i], upgradeSlotBottoms[i])) {
                // 扩展槽位检查特定操作
                if (ClientUpgradeState.isExtendedSlot(i)) {
                    // 槽位5（光灵箭）、槽位6（床）、槽位7（附魔之瓶）、槽位10（垃圾桶）可以接受点击
                    if (i != 5 && i != 6 && i != 7 && i != 10) {
                        return true; // 阻止进一步处理
                    }
                }
                
                if (button == 1) { // 右键点击
                    // 工作台升级槽位右键：打开自定义工作台界面
                    if (i == 0 && ClientUpgradeState.getStack(0) != null && !ClientUpgradeState.getStack(0).isEmpty() && !ClientUpgradeState.isSlotDisabled(0)) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.RequestOpenScreenC2SPayload(
                            com.portable.storage.net.payload.RequestOpenScreenC2SPayload.Screen.PORTABLE_CRAFTING,
                            null,
                            ""
                        ));
                        return true;
                    }
                    // 床升级槽位右键睡觉
                    if (i == 6 && ClientUpgradeState.isBedUpgradeActive()) {
                        // 发送睡觉请求到服务器
                        ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                            com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                            com.portable.storage.net.payload.StorageActionC2SPayload.Target.UPGRADE,
                            i,
                            button,
                            0,
                            "",
                            0
                        ));
                        return true;
                    }
                    // 附魔之瓶槽位右键：切换存取等级
                    if (i == 7 && ClientUpgradeState.isXpBottleUpgradeActive()) {
                        ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                            com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                            com.portable.storage.net.payload.StorageActionC2SPayload.Target.UPGRADE,
                            i,
                            button,
                            0,
                            "",
                            0
                        ));
                        return true;
                    }
                    // 垃圾桶槽位右键：清空槽位
                    if (i == 10 && ClientUpgradeState.isTrashSlotActive()) {
                        ClientPlayNetworking.send(new UpgradeSlotClickC2SPayload(i, button));
                        return true;
                    }
                    // 其他槽位右键：发送到服务器（用于切换禁用等）
                    ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                        com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                        com.portable.storage.net.payload.StorageActionC2SPayload.Target.UPGRADE,
                        i,
                        button,
                        0,
                        "",
                        0
                    ));
                    return true;
                } else if (button == 2) { // 中键点击
                    // 工作台升级槽位中键：切换虚拟合成显示状态
                    if (i == 0 && ClientUpgradeState.getStack(0) != null && !ClientUpgradeState.getStack(0).isEmpty() && !ClientUpgradeState.isSlotDisabled(0)) {
                        com.portable.storage.client.ClientConfig config = com.portable.storage.client.ClientConfig.getInstance();
                        config.virtualCraftingVisible = !config.virtualCraftingVisible;
                        com.portable.storage.client.ClientConfig.save();
                        // 切换状态时返还所有合成槽位的物品
                        com.portable.storage.client.ClientNetworkingHandlers.sendRefundCraftingSlots();
                        return true;
                    }
                    // 附魔之瓶槽位中键：切换等级维持状态
                    if (i == 7 && ClientUpgradeState.isXpBottleUpgradeActive()) {
                        com.portable.storage.client.ClientNetworkingHandlers.sendXpBottleMaintenanceToggle();
                        return true;
                    }
                    // 其他槽位切换禁用状态
                    ClientUpgradeState.toggleSlotDisabled(i);
                    // 发送禁用状态变更到服务器
                    ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                        com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                        com.portable.storage.net.payload.StorageActionC2SPayload.Target.UPGRADE,
                        i,
                        button,
                        0,
                        "",
                        0
                    ));
                    return true;
                } else if (button == 0) { // 左键点击 - 发送到服务器
                    ClientPlayNetworking.send(new UpgradeSlotClickC2SPayload(i, button));
                    return true;
                }
            }
        }
        
        // 升级槽位滚动条点击
        if (button == 0 && isOverUpgradeScrollbar(mouseX, mouseY)) {
            int height = this.upgradeScrollbarHeight;
            int upgradeVisibleRows = calculateUpgradeVisibleRows();
            
            // 计算每列的最大行数
            int leftColumnMaxRows = ClientUpgradeState.isChestUpgradeActive() ? extendedUpgradeCount : 0;
            int rightColumnMaxRows = baseUpgradeCount + 1;
            int maxRowsInAnyColumn = Math.max(leftColumnMaxRows, rightColumnMaxRows);
            
            int visible = upgradeVisibleRows;
            int total = Math.max(visible, maxRowsInAnyColumn);
            int thumbMin = 8;
            int thumbHeight = Math.max(thumbMin, (int)(height * ((float)visible / (float)total)));
            int maxScrollPx = Math.max(1, height - thumbHeight);
            int top = this.upgradeScrollbarTop;
            
            int currentThumbTop = top + (int)((maxScrollPx) * this.upgradeScroll + 0.5f);
            boolean overThumb = mouseY >= currentThumbTop && mouseY < currentThumbTop + thumbHeight;
            if (overThumb) {
                dragUpgradeScrollbar = true;
                dragUpgradeGrabOffset = (int)mouseY - currentThumbTop;
            } else {
                int mouseOffset = (int)mouseY - top - thumbHeight / 2;
                float newScroll = (float)mouseOffset / (float)maxScrollPx;
                this.upgradeScroll = Math.max(0.0f, Math.min(1.0f, newScroll));
                dragUpgradeScrollbar = true;
                dragUpgradeGrabOffset = thumbHeight / 2;
            }
            return true;
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
        
        // 仓库槽位点击（支持 Shift 快捷取出）
        // 同时支持中键（button==2）切换收藏
        if (button == 0 || button == 1 || button == 2) {
            for (int row = 0; row < visibleRows; row++) {
                for (int col = 0; col < cols; col++) {
                    int sx = gridLeft + col * (slotSize + slotSpacing);
                    int sy = gridTop + row * (slotSize + slotSpacing);
                    if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                        int visIdx = row * cols + col;
                        if (visIdx < visibleIndexMap.length) {
                            int storageIndex = visibleIndexMap[visIdx];
                            // 中键：切换收藏（仅真实物品）
                            if (button == 2 && storageIndex >= 0) {
                                var stacks = com.portable.storage.client.ClientStorageState.getStacks();
                                if (storageIndex < stacks.size()) {
                                    var st = stacks.get(storageIndex);
                                    if (st != null && !st.isEmpty()) {
                                        String id = net.minecraft.registry.Registries.ITEM.getId(st.getItem()).toString();
                                        var cfg = com.portable.storage.client.ClientConfig.getInstance();
                                        if (cfg.favorites.contains(id)) cfg.favorites.remove(id); else cfg.favorites.add(id);
                                        com.portable.storage.client.ClientConfig.save();
                                        return true;
                                    }
                                }
                            }
                             // 虚拟"瓶装经验"条目点击：发送独立消息给服务端
                             if (storageIndex == -2 && com.portable.storage.client.ClientUpgradeState.isXpBottleUpgradeActive()) {
                                 if (button == 1) {
                                     // 右键：检查是否拿着玻璃瓶进行转换
                                     MinecraftClient client = MinecraftClient.getInstance();
                                     if (client.player != null) {
                                         ItemStack cursorStack = client.player.currentScreenHandler.getCursorStack();
                                         if (!cursorStack.isEmpty() && cursorStack.isOf(net.minecraft.item.Items.GLASS_BOTTLE)) {
                                             // 发送转换请求
                                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                                        com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                                        com.portable.storage.net.payload.StorageActionC2SPayload.Target.XP_BOTTLE,
                                        0,
                                        button,
                                        0,
                                        "",
                                        0
                                    ));
                                             return true;
                                         }
                                     }
                                 }
                                 // 左键或其他情况：正常的经验存取
                                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                                    com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                                    com.portable.storage.net.payload.StorageActionC2SPayload.Target.XP_BOTTLE,
                                    0,
                                    button,
                                    0,
                                    "",
                                    0
                                ));
                                 return true;
                             }
                             // 虚拟流体条目点击：发送独立消息给服务端
                             else if (storageIndex < Integer.MIN_VALUE + 1000) {
                                String fluidType = getFluidTypeFromVirtualIndex(storageIndex);
                                 if (fluidType != null) {
                                     if (button == 1) {
                                         // 右键：检查是否拿着空桶进行转换
                                         MinecraftClient client = MinecraftClient.getInstance();
                                         if (client.player != null) {
                                             ItemStack cursorStack = client.player.currentScreenHandler.getCursorStack();
                                             if (!cursorStack.isEmpty() && cursorStack.isOf(net.minecraft.item.Items.BUCKET)) {
                                                 // 发送流体转换请求
                                                 net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.FluidConversionC2SPayload(fluidType, button));
                                                 return true;
                                             }
                                         }
                                     }
                                    // 左键点击虚拟流体：检查是否开启自动传入且按着shift
                                    if (button == 0) {
                                        boolean shift = isShiftDown();
                                        if (shift && ClientConfig.getInstance().autoDeposit) {
                                            // Shift+左键：检查仓库或背包中是否有桶，消耗一个桶取出对应流体桶
                                            MinecraftClient client = MinecraftClient.getInstance();
                                            if (client.player != null) {
                                                // 检查背包中是否有空桶
                                                boolean hasBucket = false;
                                                var handler = client.player.currentScreenHandler;
                                                for (int i = 0; i < handler.slots.size(); i++) {
                                                    var slot = handler.slots.get(i);
                                                    if (slot.hasStack() && slot.getStack().isOf(net.minecraft.item.Items.BUCKET)) {
                                                        hasBucket = true;
                                                        break;
                                                    }
                                                }
                                                
                                                if (hasBucket) {
                                                    // 发送流体转换请求（左键）
                                                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                                                        com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                                                        com.portable.storage.net.payload.StorageActionC2SPayload.Target.FLUID,
                                                        0,
                                                        button,
                                                        0,
                                                        fluidType,
                                                        0
                                                    ));
                                                    return true;
                                                }
                                            }
                                        }
                                        return true;
                                    }
                                 }
                             }
                            if (storageIndex >= 0) {
                                // 优先处理：智能折叠代表项的右键 → 展开（填充搜索框），避免与“右键取1个”冲突
                                if (button == 1 && ClientConfig.getInstance().smartCollapse && collapsedInfoByRep.containsKey(storageIndex)) {
                                    if (this.searchField == null || this.searchField.getText().isEmpty()) {
                                        String id = collapsedInfoByRep.get(storageIndex).itemId;
                                        this.query = id;
                                        if (this.searchField != null) this.searchField.setText(id);
                                        return true;
                                    }
                                }
                                // 常规取物：Shift+左键=一组，Shift+右键=一个；否则遵循统一槽位点击语义
                                boolean shift = isShiftDown();
                                if (shift) {
                                    ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                                        com.portable.storage.net.payload.StorageActionC2SPayload.Action.SHIFT_TAKE,
                                        com.portable.storage.net.payload.StorageActionC2SPayload.Target.STORAGE,
                                        storageIndex,
                                        button,
                                        0,
                                        "",
                                        0
                                    ));
                                } else {
                                    ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                                        com.portable.storage.net.payload.StorageActionC2SPayload.Action.CLICK,
                                        com.portable.storage.net.payload.StorageActionC2SPayload.Target.STORAGE,
                                        storageIndex,
                                        button,
                                        0,
                                        "",
                                        0
                                    ));
                                }
                                return true;
                            } else {
                                // 空格子：若鼠标上有物品，则投递到仓库
                                var mc = MinecraftClient.getInstance();
                                if (mc != null && mc.player != null) {
                                    ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
                                    if (!cursor.isEmpty()) {
                                ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                                    com.portable.storage.net.payload.StorageActionC2SPayload.Action.DEPOSIT_CURSOR,
                                    com.portable.storage.net.payload.StorageActionC2SPayload.Target.STORAGE,
                                    0,
                                    button,
                                    0,
                                    "",
                                    0
                                ));
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
        if (button == 0 || button == 1) {
            // 折叠仓库（仅在enableCollapse为true时）
            if (enableCollapse && isIn(mouseX, mouseY, collapseLeft, collapseTop, collapseRight, collapseBottom)) {
                collapsed = true;
                config.collapsed = true;
                ClientConfig.save();
                if (this.searchField != null) this.searchField.setFocused(false);
                return true;
            }
            // 非搜索状态右键折叠项：自动搜索完整物品ID（展开）
            if (button == 1) {
                int hovered = resolveHoveredIndex((int)mouseX, (int)mouseY);
                if (hovered >= 0 && collapsedInfoByRep.containsKey(hovered)) {
                    String id = collapsedInfoByRep.get(hovered).itemId;
                    String text = id;
                    this.query = text;
                    if (this.searchField != null) this.searchField.setText(text);
                    return true;
                }
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
            if (isIn(mouseX, mouseY, smartCollapseLeft, smartCollapseTop, smartCollapseRight, smartCollapseBottom)) {
                config.smartCollapse = !config.smartCollapse;
                ClientConfig.save();
                return true;
            }
            // 搜索位置切换（只在背包和工作台界面响应）
            if (portableStorage$isInventoryOrCraftingScreen() && isIn(mouseX, mouseY, searchPosLeft, searchPosTop, searchPosRight, searchPosBottom)) {
                if (config.storagePos == ClientConfig.StoragePos.TOP) {
                    // 仓库在顶部时，搜索位置只能在顶部、中间、底部三个状态中循环
                    switch (config.searchPos) {
                        case TOP:
                            config.searchPos = ClientConfig.SearchPos.MIDDLE;
                            break;
                        case MIDDLE:
                            config.searchPos = ClientConfig.SearchPos.BOTTOM;
                            break;
                        case BOTTOM:
                        default:
                            config.searchPos = ClientConfig.SearchPos.TOP;
                            break;
                        case TOP2:
                            // 特殊情况：如果当前是TOP2，则视为TOP，切换到中间
                            config.searchPos = ClientConfig.SearchPos.MIDDLE;
                            break;
                    }
                } else {
                    // 仓库在底部时，使用原有的循环逻辑
                    config.searchPos = config.searchPos.next();
                }
                ClientConfig.save();
                return true;
            }
            // 仓库位置切换
            if (isIn(mouseX, mouseY, storagePosLeft, storagePosTop, storagePosRight, storagePosBottom)) {
                ClientConfig.StoragePos oldPos = config.storagePos;
                config.storagePos = config.storagePos.next();
                
                // 当仓库位置切换到顶部时，自动将搜索位置设置为中间
                if (oldPos == ClientConfig.StoragePos.BOTTOM && config.storagePos == ClientConfig.StoragePos.TOP) {
                    config.searchPos = ClientConfig.SearchPos.MIDDLE;
                }
                
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
        // 检查是否在升级槽位区域
        if (isOverUpgradeArea(mouseX, mouseY)) {
            int upgradeVisibleRows = calculateUpgradeVisibleRows();
            
            // 计算每列的最大行数
            int leftColumnMaxRows = ClientUpgradeState.isChestUpgradeActive() ? extendedUpgradeCount : 0;
            int rightColumnMaxRows = baseUpgradeCount + 1;
            int maxRowsInAnyColumn = Math.max(leftColumnMaxRows, rightColumnMaxRows);
            int maxUpgradeScrollRows = Math.max(0, maxRowsInAnyColumn - upgradeVisibleRows);
            
            if (maxUpgradeScrollRows > 0) {
                float delta = (float)verticalAmount * -0.1f;
                this.upgradeScroll = Math.max(0.0f, Math.min(1.0f, this.upgradeScroll + delta));
                return true;
            } else {
                // 如果没有滚动内容，重置滚动位置
                this.upgradeScroll = 0.0f;
                return true;
            }
        }
        
        // 检查是否在仓库区域
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

        // 自动传入启用时支持 Q / Ctrl+Q 从仓库丢出悬停物品
        if (ClientStorageState.isStorageEnabled() && ClientConfig.getInstance().autoDeposit) {
            // 获取当前鼠标位置以判定悬停槽位
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.mouse != null) {
                double mx = mc.mouse.getX() * (double)mc.getWindow().getScaledWidth() / (double)mc.getWindow().getWidth();
                double my = mc.mouse.getY() * (double)mc.getWindow().getScaledHeight() / (double)mc.getWindow().getHeight();
                if (isOverStorageArea(mx, my)) {
                    // Q 键
                    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Q) {
                        int hovered = resolveHoveredIndex((int)mx, (int)my);
                        if (hovered >= 0) {
                            boolean ctrl = isCtrlDown();
                            int amountType = ctrl ? 1 : 0; // 1 组 或 1 个
                            ClientPlayNetworking.send(new com.portable.storage.net.payload.StorageActionC2SPayload(
                                com.portable.storage.net.payload.StorageActionC2SPayload.Action.DROP,
                                com.portable.storage.net.payload.StorageActionC2SPayload.Target.STORAGE,
                                hovered,
                                0,
                                0,
                                "",
                                amountType
                            ));
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isShiftDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long win = mc.getWindow().getHandle();
        boolean left = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        boolean right = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return left || right;
    }
    
    public boolean charTyped(char chr, int modifiers) {
        if (searchField != null && searchField.charTyped(chr, modifiers)) {
            return true;
        }
        return false;
    }

    private int resolveHoveredIndex(int mouseX, int mouseY) {
        int col = (mouseX - gridLeft) / (slotSize + slotSpacing);
        int row = (mouseY - gridTop) / (slotSize + slotSpacing);
        if (col < 0 || col >= cols || row < 0 || row >= visibleRows) return -1;
        int visIdx = row * cols + col;
        if (visIdx < 0 || visIdx >= visibleIndexMap.length) return -1;
        return visibleIndexMap[visIdx];
    }

    private boolean isCtrlDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long win = mc.getWindow().getHandle();
        boolean left = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        boolean right = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return left || right;
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
    
    private boolean isOverUpgradeScrollbar(double mouseX, double mouseY) {
        return mouseX >= upgradeScrollbarLeft && mouseX < upgradeScrollbarLeft + upgradeScrollbarWidth && 
               mouseY >= upgradeScrollbarTop && mouseY < upgradeScrollbarTop + upgradeScrollbarHeight;
    }
    
    private boolean isOverStorageArea(double mouseX, double mouseY) {
        int width = cols * (slotSize + slotSpacing);
        int height = visibleRows * (slotSize + slotSpacing);
        return mouseX >= gridLeft && mouseX < gridLeft + width && mouseY >= gridTop && mouseY < gridTop + height;
    }
    
    /**
     * 检查鼠标是否在升级槽位区域
     */
    private boolean isOverUpgradeArea(double mouseX, double mouseY) {
        int upgradeLeft = gridLeft - upgradeSlotSize - upgradeSpacing - 2;
        int upgradeTop = gridTop;
        int upgradeWidth = upgradeSlotSize;
        int upgradeVisibleRows = calculateUpgradeVisibleRows();
        int upgradeHeight = upgradeVisibleRows * (upgradeSlotSize + upgradeSpacing);
        
        // 检查基础升级槽位区域
        if (mouseX >= upgradeLeft && mouseX < upgradeLeft + upgradeWidth && 
            mouseY >= upgradeTop && mouseY < upgradeTop + upgradeHeight) {
            return true;
        }
        
        // 检查扩展升级槽位区域（如果激活）
        if (ClientUpgradeState.isChestUpgradeActive()) {
            int extendedLeft = upgradeLeft - (upgradeSlotSize + upgradeSpacing + 2);
            if (mouseX >= extendedLeft && mouseX < extendedLeft + upgradeSlotSize && 
                mouseY >= upgradeTop && mouseY < upgradeTop + upgradeHeight) {
                return true;
            }
        }
        
        return false;
    }
    
    public boolean isOverAnyComponent(double mouseX, double mouseY) {
        return isOverStorageArea(mouseX, mouseY) || isOverScrollbar(mouseX, mouseY) || isOverUpgradeArea(mouseX, mouseY);
    }
    
    
    /**
     * 检查鼠标是否悬停在升级槽位上
     */
    private boolean portableStorage$isOverUpgradeSlot(double mouseX, double mouseY) {
        for (int i = 0; i < totalUpgradeCount; i++) {
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
        for (int i = 0; i < totalUpgradeCount; i++) {
            if (isIn(mouseX, mouseY, upgradeSlotLefts[i], upgradeSlotTops[i], upgradeSlotRights[i], upgradeSlotBottoms[i])) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 根据虚拟索引获取流体类型
     */
    private String getFluidTypeFromVirtualIndex(int virtualIndex) {
        return switch (virtualIndex) {
            case Integer.MIN_VALUE + 1 -> "lava";
            case Integer.MIN_VALUE + 2 -> "water";
            case Integer.MIN_VALUE + 3 -> "milk";
            default -> null;
        };
    }
    
    /**
     * 创建流体显示物品
     */
    private ItemStack createFluidDisplayStack(String fluidType) {
        return switch (fluidType) {
            case "lava" -> new ItemStack(net.minecraft.item.Items.LAVA_BUCKET);
            case "water" -> new ItemStack(net.minecraft.item.Items.WATER_BUCKET);
            case "milk" -> new ItemStack(net.minecraft.item.Items.MILK_BUCKET);
            default -> ItemStack.EMPTY;
        };
    }
    
    /**
     * 绘制自定义流体贴图
     */
    private void drawFluidTexture(DrawContext context, String fluidType, int x, int y) {
        net.minecraft.util.Identifier textureId = switch (fluidType) {
            case "lava" -> net.minecraft.util.Identifier.of("portable-storage", "textures/item/lava.png");
            case "water" -> net.minecraft.util.Identifier.of("portable-storage", "textures/item/water.png");
            case "milk" -> net.minecraft.util.Identifier.of("portable-storage", "textures/item/milk.png");
            default -> null;
        };
        
        if (textureId != null) {
            context.drawTexture(textureId, x, y, 0, 0, 16, 16, 16, 16);
        }
    }
    
    /**
     * 检查当前界面是否为背包或工作台界面
     */
    private boolean portableStorage$isInventoryOrCraftingScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen == null) {
            return false;
        }
        
        return client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen ||
               client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CraftingScreen ||
               client.currentScreen instanceof com.portable.storage.client.screen.PortableCraftingScreen;
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
            case 5: key = "item.minecraft.spectral_arrow"; break; // 光灵箭升级
            case 6: key = "block.minecraft.red_bed"; break; // 床升级
            case 7: key = "item.minecraft.experience_bottle"; break; // 附魔之瓶升级
            case 8: case 9: case 10: 
                key = "portable_storage.upgrade.extended_slot"; break;
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


