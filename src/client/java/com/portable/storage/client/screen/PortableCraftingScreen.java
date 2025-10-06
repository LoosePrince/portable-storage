package com.portable.storage.client.screen;

import com.portable.storage.client.ClientStorageState;
import com.portable.storage.client.ScreenSwapBypass;
import com.portable.storage.client.ui.StorageUIComponent;
import com.portable.storage.net.payload.EmiRecipeFillC2SPayload;
import com.portable.storage.net.payload.RequestVanillaCraftingOpenC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import com.portable.storage.screen.PortableCraftingScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;

import java.util.function.Consumer;

/**
 * 自定义工作台界面：
 * - 复刻原版工作台界面布局与交互
 * - 去除合成书按钮（不显示配方书按钮）
 * - 提供按钮切换回原版工作台界面
 * - 集成仓库 UI 叠加
 */
public class PortableCraftingScreen extends HandledScreen<PortableCraftingScreenHandler> {
    private static final Identifier BACKGROUND_TEXTURE = Identifier.of("minecraft", "textures/gui/container/crafting_table.png");
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 166;
    // 仓库UI相关高度常量
    private static final int STORAGE_UI_HEIGHT = 108; // 6行 * 18像素
    private static final int GAP_BETWEEN = 6; // 工作台与仓库UI之间的间距

    private final PlayerInventory playerInventoryRef;
    private final Text titleRef;
    private final StorageUIComponent storageUi = new StorageUIComponent();

    public PortableCraftingScreen(PortableCraftingScreenHandler handler, PlayerInventory playerInventory, Text title) {
        super(handler, playerInventory, title);
        this.playerInventoryRef = playerInventory;
        this.titleRef = title;
        this.backgroundWidth = BG_WIDTH;
        this.backgroundHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        
        // 计算整体内容高度：工作台 + 间距 + 仓库UI
        int totalContentHeight = BG_HEIGHT + GAP_BETWEEN + STORAGE_UI_HEIGHT;
        
        // 计算居中位置：让整体内容在屏幕中垂直居中
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - totalContentHeight) / 2;

        // 初始化仓库搜索框（不折叠）
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            int fieldWidth = 120;
            int fieldHeight = 18;
            int fieldX = this.x + this.backgroundWidth - fieldWidth - 8;
            int fieldY = this.y - fieldHeight - 6;
            storageUi.initSearchField(client, fieldX, fieldY, fieldWidth, fieldHeight);
            storageUi.setCollapsed(false);
        }

        // 设置切换原版界面的回调：请求服务端打开原版工作台
        storageUi.setSwitchToVanillaCallback(() -> {
            ScreenSwapBypass.requestSkipNextCraftingSwap();
            ClientPlayNetworking.send(new RequestVanillaCraftingOpenC2SPayload());
        });
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x0 = this.x;
        int y0 = this.y;
        context.drawTexture(BACKGROUND_TEXTURE, x0, y0, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 渲染仓库 UI 叠加
        storageUi.render(context, mouseX, mouseY, delta, this.x, this.y, this.backgroundWidth, this.backgroundHeight);

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (storageUi.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (storageUi.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (storageUi.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }
    
    /**
     * 检查鼠标是否在仓库区域
     */
    public boolean isMouseOverStorageArea(int mouseX, int mouseY) {
        return storageUi.isMouseOverStorageArea(mouseX, mouseY);
    }
    
    /**
     * 获取鼠标下的仓库物品
     */
    public ItemStack getStorageItemUnderMouse(int mouseX, int mouseY) {
        return storageUi.getItemUnderMouse(mouseX, mouseY);
    }
    
    /**
     * 获取 EMI 排除区域
     */
    public void getExclusionAreas(Consumer<Bounds> consumer) {
        // 排除仓库UI区域
        int storageLeft = this.x + 8;
        int storageTop = this.y + this.backgroundHeight + 6;
        int storageWidth = 9 * 18; // 9列
        int storageHeight = 6 * 18; // 6行
        
        consumer.accept(new Bounds(storageLeft, storageTop, storageWidth, storageHeight));
        
        // 排除升级槽位区域
        int upgradeLeft = this.x - 24;
        int upgradeTop = storageTop;
        int upgradeWidth = 18;
        int upgradeHeight = 5 * 18; // 5个槽位
        
        consumer.accept(new Bounds(upgradeLeft, upgradeTop, upgradeWidth, upgradeHeight));
        
        // 排除设置面板区域
        int settingsLeft = this.x + this.backgroundWidth + 6;
        int settingsTop = storageTop;
        int settingsWidth = 100; // 估算宽度
        int settingsHeight = storageHeight;
        
        consumer.accept(new Bounds(settingsLeft, settingsTop, settingsWidth, settingsHeight));
    }
    
    /**
     * 从仓库填充配方
     */
    public void fillRecipeFromStorage(EmiRecipe recipe) {
        var recipeId = recipe.getId();
        if (recipeId == null) return;
        
        var inputs = recipe.getInputs();
        if (inputs.isEmpty()) return;
        
        // 准备槽位索引和物品数量
        var slotIndices = new java.util.ArrayList<Integer>();
        var itemCounts = new java.util.ArrayList<Integer>();
        
        // 为每个输入槽位准备数据
        for (int i = 0; i < Math.min(inputs.size(), 9); i++) {
            var input = inputs.get(i);
            var emiStacks = input.getEmiStacks();
            
            if (!emiStacks.isEmpty()) {
                var emiStack = emiStacks.get(0); // 取第一个可用的物品
                ItemStack stack = emiStack.getItemStack();
                if (!stack.isEmpty()) {
                    slotIndices.add(i + 1); // 合成槽位从1开始
                    itemCounts.add(stack.getCount());
                }
            }
        }
        
        // 发送网络包
        if (!slotIndices.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger("portable-storage/emi").debug("Client send EmiRecipeFill: recipeId={}, slots={}, counts={}", recipeId, slotIndices, itemCounts);
            int[] slotArray = slotIndices.stream().mapToInt(Integer::intValue).toArray();
            int[] countArray = itemCounts.stream().mapToInt(Integer::intValue).toArray();
            
            ClientPlayNetworking.send(new EmiRecipeFillC2SPayload(
                recipeId.toString(),
                slotArray,
                countArray
            ));
        }
    }
}


