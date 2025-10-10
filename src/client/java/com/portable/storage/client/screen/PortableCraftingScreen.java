package com.portable.storage.client.screen;

import com.portable.storage.client.ClientStorageState;
import com.portable.storage.client.ScreenSwapBypass;
import com.portable.storage.client.ui.StorageUIComponent;
import com.portable.storage.net.payload.EmiRecipeFillC2SPayload;
import com.portable.storage.net.payload.RequestSyncC2SPayload;
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

    // 合成补充相关字段
    private final java.util.Map<Integer, ItemStack> portableStorage$lastCraftingStacks = new java.util.HashMap<>();
    private ItemStack portableStorage$lastCraftingOutput = ItemStack.EMPTY;
    private long portableStorage$lastCraftRefillCheck = 0;
    private long portableStorage$lastCraftingSlotClickTime = 0;
    
    // 配置变化监听
    private com.portable.storage.client.ClientConfig.StoragePos portableStorage$lastStoragePos = null;

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
        
        // 根据仓库位置设置计算整体内容高度和位置
        com.portable.storage.client.ClientConfig config = com.portable.storage.client.ClientConfig.getInstance();
        int totalContentHeight;
        
        if (config.storagePos == com.portable.storage.client.ClientConfig.StoragePos.TOP) {
            // 仓库在顶部时：仓库UI + 间距 + 工作台
            totalContentHeight = STORAGE_UI_HEIGHT + GAP_BETWEEN + BG_HEIGHT;
            // 计算居中位置：让整体内容在屏幕中垂直居中
            this.x = (this.width - this.backgroundWidth) / 2;
            this.y = (this.height - totalContentHeight) / 2;
            // 工作台界面需要下移：y + 仓库UI高度 + 间距
            this.y = this.y + STORAGE_UI_HEIGHT + GAP_BETWEEN;
        } else {
            // 仓库在底部时：工作台 + 间距 + 仓库UI
            totalContentHeight = BG_HEIGHT + GAP_BETWEEN + STORAGE_UI_HEIGHT;
            // 计算居中位置：让整体内容在屏幕中垂直居中
            this.x = (this.width - this.backgroundWidth) / 2;
            this.y = (this.height - totalContentHeight) / 2;
        }

        // 初始化仓库搜索框（不折叠）
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            int fieldWidth = 120;
            int fieldHeight = 18;
            int fieldX = this.x + this.backgroundWidth - fieldWidth - 8;
            int fieldY;
            
            if (config.storagePos == com.portable.storage.client.ClientConfig.StoragePos.TOP) {
                // 仓库在顶部时，搜索框在仓库上方
                fieldY = this.y - fieldHeight - 6;
            } else {
                // 仓库在底部时，搜索框在工作台上方
                fieldY = this.y - fieldHeight - 6;
            }
            
            storageUi.initSearchField(client, fieldX, fieldY, fieldWidth, fieldHeight);
            storageUi.setCollapsed(false);
        }

        // 设置切换原版界面的回调：请求服务端打开原版工作台
        storageUi.setSwitchToVanillaCallback(() -> {
            ScreenSwapBypass.requestSkipNextCraftingSwap();
            ClientPlayNetworking.send(new RequestVanillaCraftingOpenC2SPayload());
        });

        // 标记开始查看仓库界面
        com.portable.storage.sync.PlayerViewState.startViewing(MinecraftClient.getInstance().player.getUuid());
        
        // 打开自定义工作台界面时请求同步仓库数据
        ClientPlayNetworking.send(RequestSyncC2SPayload.INSTANCE);
        
        // 初始化配置监听
        this.portableStorage$lastStoragePos = config.storagePos;
    }
    
    /**
     * 重新计算界面位置
     */
    private void portableStorage$recalculatePosition() {
        com.portable.storage.client.ClientConfig config = com.portable.storage.client.ClientConfig.getInstance();
        int totalContentHeight;
        
        if (config.storagePos == com.portable.storage.client.ClientConfig.StoragePos.TOP) {
            // 仓库在顶部时：仓库UI + 间距 + 工作台
            totalContentHeight = STORAGE_UI_HEIGHT + GAP_BETWEEN + BG_HEIGHT;
            // 计算居中位置：让整体内容在屏幕中垂直居中
            this.x = (this.width - this.backgroundWidth) / 2;
            this.y = (this.height - totalContentHeight) / 2;
            // 工作台界面需要下移：y + 仓库UI高度 + 间距
            this.y = this.y + STORAGE_UI_HEIGHT + GAP_BETWEEN;
        } else {
            // 仓库在底部时：工作台 + 间距 + 仓库UI
            totalContentHeight = BG_HEIGHT + GAP_BETWEEN + STORAGE_UI_HEIGHT;
            // 计算居中位置：让整体内容在屏幕中垂直居中
            this.x = (this.width - this.backgroundWidth) / 2;
            this.y = (this.height - totalContentHeight) / 2;
        }
        
        // 重新初始化搜索框位置
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            int fieldWidth = 120;
            int fieldHeight = 18;
            int fieldX = this.x + this.backgroundWidth - fieldWidth - 8;
            int fieldY;
            
            if (config.storagePos == com.portable.storage.client.ClientConfig.StoragePos.TOP) {
                // 仓库在顶部时，搜索框在仓库上方
                fieldY = this.y - fieldHeight - 6;
            } else {
                // 仓库在底部时，搜索框在工作台上方
                fieldY = this.y - fieldHeight - 6;
            }
            
            storageUi.initSearchField(client, fieldX, fieldY, fieldWidth, fieldHeight);
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x0 = this.x;
        int y0 = this.y;
        context.drawTexture(BACKGROUND_TEXTURE, x0, y0, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public void close() {
        // 标记停止查看仓库界面
        if (MinecraftClient.getInstance().player != null) {
            com.portable.storage.sync.PlayerViewState.stopViewing(MinecraftClient.getInstance().player.getUuid());
        }
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 检查配置是否发生变化
        com.portable.storage.client.ClientConfig config = com.portable.storage.client.ClientConfig.getInstance();
        if (this.portableStorage$lastStoragePos != config.storagePos) {
            // 配置发生变化，重新计算位置
            portableStorage$recalculatePosition();
            this.portableStorage$lastStoragePos = config.storagePos;
        }
        
        super.render(context, mouseX, mouseY, delta);

        // 检查仓库是否已启用
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled()) {
            // 渲染仓库 UI 叠加
            storageUi.render(context, mouseX, mouseY, delta, this.x, this.y, this.backgroundWidth, this.backgroundHeight);
        }

        this.drawMouseoverTooltip(context, mouseX, mouseY);

        // 合成补充检测
        portableStorage$checkCraftRefill();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 记录点击合成槽位用于抑制立即补充
        var handler = this.handler;
        if (handler != null) {
            int screenX = this.x;
            int screenY = this.y;
            for (net.minecraft.screen.slot.Slot slot : handler.slots) {
                int slotX = screenX + slot.x;
                int slotY = screenY + slot.y;
                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    if (slot.id >= 1 && slot.id <= 9) {
                        portableStorage$lastCraftingSlotClickTime = System.currentTimeMillis();
                    }
                    break;
                }
            }
        }

        if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && storageUi.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && storageUi.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && storageUi.charTyped(chr, modifiers)) return true;
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
        com.portable.storage.client.ClientConfig config = com.portable.storage.client.ClientConfig.getInstance();
        
        // 排除仓库UI区域
        int storageLeft = this.x + 8;
        int storageTop;
        int storageWidth = 9 * 18; // 9列
        int storageHeight = 6 * 18; // 6行
        
        if (config.storagePos == com.portable.storage.client.ClientConfig.StoragePos.TOP) {
            // 仓库在顶部时
            storageTop = this.y - storageHeight - 6;
        } else {
            // 仓库在底部时
            storageTop = this.y + this.backgroundHeight + 6;
        }
        
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
    
    // ===== 合成补充逻辑 =====
    private void portableStorage$checkCraftRefill() {
        // 检查合成补充开关
        if (!com.portable.storage.client.ClientConfig.getInstance().craftRefill) return;

        long now = System.currentTimeMillis();
        if (now - portableStorage$lastCraftRefillCheck < 100) return; // 100ms 限流
        portableStorage$lastCraftRefillCheck = now;

        var handler = this.handler;
        if (handler == null) return;

        boolean craftOccurred = false;
        // 输出槽为 index 0
        if (handler.slots.size() > 0) {
            ItemStack currentOutput = handler.getSlot(0).getStack();
            if (!portableStorage$lastCraftingOutput.isEmpty()) {
                if (currentOutput.isEmpty() ||
                    (!ItemStack.areItemsAndComponentsEqual(currentOutput, portableStorage$lastCraftingOutput)) ||
                    (currentOutput.getCount() < portableStorage$lastCraftingOutput.getCount())) {
                    craftOccurred = true;
                }
            }
            portableStorage$lastCraftingOutput = currentOutput.copy();
        }

        if (craftOccurred) {
            for (int i = 1; i <= 9; i++) {
                if (handler.slots.size() <= i) continue;
                ItemStack currentStack = handler.getSlot(i).getStack();
                ItemStack lastStack = portableStorage$lastCraftingStacks.get(i);
                boolean recentClick = (now - portableStorage$lastCraftingSlotClickTime) < 300;

                if (lastStack != null && !lastStack.isEmpty()) {
                    if (currentStack.isEmpty()) {
                        if (!recentClick) {
                            ItemStack target = lastStack.copy();
                            target.setCount(target.getMaxCount());
                            portableStorage$refillFromStorage(i, target);
                        }
                    } else if (ItemStack.areItemsAndComponentsEqual(currentStack, lastStack) && currentStack.getCount() < lastStack.getCount()) {
                        if (!recentClick) {
                            ItemStack target = currentStack.copy();
                            target.setCount(target.getMaxCount());
                            portableStorage$refillFromStorage(i, target);
                        }
                    }
                }
            }
        }

        // 更新输入缓存
        for (int i = 1; i <= 9; i++) {
            if (handler.slots.size() <= i) continue;
            ItemStack cur = handler.getSlot(i).getStack();
            portableStorage$lastCraftingStacks.put(i, cur.copy());
        }
    }

    private void portableStorage$refillFromStorage(int slotIndex, ItemStack targetStack) {
        if (targetStack.isEmpty()) return;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.RefillCraftingC2SPayload(slotIndex, targetStack));
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


