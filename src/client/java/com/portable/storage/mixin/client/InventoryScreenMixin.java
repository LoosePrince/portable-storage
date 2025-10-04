package com.portable.storage.mixin.client;

import com.portable.storage.PortableStorage;
import com.portable.storage.client.ClientConfig;
import com.portable.storage.client.ui.StorageUIComponent;
import com.portable.storage.net.payload.RefillCraftingC2SPayload;
import com.portable.storage.net.payload.RequestSyncC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {

    @Unique
    private final StorageUIComponent portableStorage$uiComponent = new StorageUIComponent();

    // 合成补充相关字段
    @Unique private final java.util.Map<Integer, ItemStack> portableStorage$lastCraftingStacks = new java.util.HashMap<>();
    @Unique private ItemStack portableStorage$lastCraftingOutput = ItemStack.EMPTY;
    @Unique private long portableStorage$lastCraftRefillCheck = 0;
    @Unique private long portableStorage$lastCraftingSlotClickTime = 0;

    @Inject(method = "init", at = @At("TAIL"))
    private void portableStorage$init(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        InventoryScreen self = (InventoryScreen)(Object)this;
        int screenWidth = self.width;
        int screenHeight = self.height;
        int backgroundWidth = 176;
        int backgroundHeight = 166;
        int x = (screenWidth - backgroundWidth) / 2;
        int y = (screenHeight - backgroundHeight) / 2;

        // 初始化UI组件的搜索框
        int fieldWidth = 120;
        int fieldHeight = 18;
        int fieldX = x + backgroundWidth - fieldWidth - 8;
        int fieldY = y - fieldHeight - 6;
        portableStorage$uiComponent.initSearchField(client, fieldX, fieldY, fieldWidth, fieldHeight);

        // 加载折叠状态
        portableStorage$uiComponent.loadCollapsedState();

        PortableStorage.LOGGER.debug("Portable Storage: UI component initialized, collapsed={}", portableStorage$uiComponent.isCollapsed());

        // 打开界面时请求同步
        ClientPlayNetworking.send(RequestSyncC2SPayload.INSTANCE);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void portableStorage$renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen)(Object)this;
        int screenWidth = self.width;
        int screenHeight = self.height;
        int backgroundWidth = 176;
        int backgroundHeight = 166;
        int x = (screenWidth - backgroundWidth) / 2;
        int y = (screenHeight - backgroundHeight) / 2;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.textRenderer != null) {
            // 渲染仓库UI（启用折叠功能）
            portableStorage$uiComponent.render(context, mouseX, mouseY, delta, x, y, backgroundWidth, backgroundHeight, true);
        }

        // 合成补充检测（在render之外也调用，避免折叠时失效）
        portableStorage$checkCraftRefill();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void portableStorage$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 检测是否点击了合成槽位（索引 1-4），用于暂停自动补充
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            var handler = mc.player.currentScreenHandler;
            if (handler instanceof net.minecraft.screen.PlayerScreenHandler) {
        InventoryScreen self = (InventoryScreen)(Object)this;
        int screenWidth = self.width;
        int screenHeight = self.height;
        int backgroundWidth = 176;
        int backgroundHeight = 166;
                int screenX = (screenWidth - backgroundWidth) / 2;
                int screenY = (screenHeight - backgroundHeight) / 2;
                
                // 遍历所有槽位检测鼠标位置
                for (net.minecraft.screen.slot.Slot slot : handler.slots) {
                    int slotX = screenX + slot.x;
                    int slotY = screenY + slot.y;
                    if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                        // 点击了这个槽位
                        if (slot.id >= 1 && slot.id <= 4) {
                            // 点击了合成输入槽位，记录时间
                            portableStorage$lastCraftingSlotClickTime = System.currentTimeMillis();
                        }
                        break;
                    }
                }
            }
        }

        // 委托给UI组件处理（启用折叠功能）
        if (portableStorage$uiComponent.mouseClicked(mouseX, mouseY, button, true)) {
                cir.setReturnValue(true);
            return;
        }
    }

    // 注意：InventoryScreen 可能没有 mouseScrolled 方法，我们通过其他方式处理滚轮事件

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void portableStorage$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (portableStorage$uiComponent.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void portableStorage$charTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (portableStorage$uiComponent.charTyped(chr, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 检测合成补充需求并发送请求
     */
    @Unique
    private void portableStorage$checkCraftRefill() {
        // 检查合成补充功能是否启用
        if (!ClientConfig.getInstance().craftRefill) {
            return;
        }
        
        // 限流：每100ms最多检查一次
        long now = System.currentTimeMillis();
        if (now - portableStorage$lastCraftRefillCheck < 100) {
            return;
        }
        portableStorage$lastCraftRefillCheck = now;
        
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        
        var handler = mc.player.currentScreenHandler;
        if (!(handler instanceof net.minecraft.screen.PlayerScreenHandler)) {
            return;
        }
        
        // PlayerScreenHandler 的合成槽位：
        // 0 = 输出, 1-4 = 输入
        boolean craftOccurred = false;
        
        // 检测合成输出槽位（索引0）的变化
        if (handler.slots.size() > 0) {
            ItemStack currentOutput = handler.getSlot(0).getStack();
            
            // 如果输出槽位变空或数量减少，说明发生了合成
            if (!portableStorage$lastCraftingOutput.isEmpty()) {
                if (currentOutput.isEmpty() || 
                    (!ItemStack.areItemsAndComponentsEqual(currentOutput, portableStorage$lastCraftingOutput)) ||
                    (currentOutput.getCount() < portableStorage$lastCraftingOutput.getCount())) {
                    craftOccurred = true;
                }
            }
            
            // 更新输出缓存
            portableStorage$lastCraftingOutput = currentOutput.copy();
        }
        
        // 如果发生了合成，检查输入槽位的消耗
        if (craftOccurred) {
            for (int i = 1; i <= 4; i++) {
                if (handler.slots.size() <= i) continue;
                
                ItemStack currentStack = handler.getSlot(i).getStack();
                ItemStack lastStack = portableStorage$lastCraftingStacks.get(i);
                
                // 检测是否有物品被消耗
                if (lastStack != null && !lastStack.isEmpty()) {
                    // 检测最近是否点击了合成槽位（300ms内）
                    boolean recentClick = (now - portableStorage$lastCraftingSlotClickTime) < 300;
                    
                    if (currentStack.isEmpty()) {
                        // 物品被完全消耗，需要补充
                        if (!recentClick) {
                            // 补充到最大堆叠数量
                            ItemStack targetStack = lastStack.copy();
                            targetStack.setCount(targetStack.getMaxCount());
                            portableStorage$refillFromStorage(i, targetStack);
                        }
                    } else if (ItemStack.areItemsAndComponentsEqual(currentStack, lastStack) && 
                               currentStack.getCount() < lastStack.getCount()) {
                        // 物品部分消耗，需要补充
                        if (!recentClick) {
                            // 补充到最大堆叠数量
                            ItemStack targetStack = currentStack.copy();
                            targetStack.setCount(targetStack.getMaxCount());
                            portableStorage$refillFromStorage(i, targetStack);
                        }
                    }
                }
            }
        }
        
        // 更新输入槽位缓存
        for (int i = 1; i <= 4; i++) {
            if (handler.slots.size() <= i) continue;
            ItemStack currentStack = handler.getSlot(i).getStack();
            portableStorage$lastCraftingStacks.put(i, currentStack.copy());
        }
    }
    
    /**
     * 从仓库补充物品到合成槽位
     */
    @Unique
    private void portableStorage$refillFromStorage(int slotIndex, ItemStack targetStack) {
        if (targetStack.isEmpty()) {
            return;
        }
        
        // 发送补充请求到服务器
        ClientPlayNetworking.send(new RefillCraftingC2SPayload(slotIndex, targetStack));
    }
}
