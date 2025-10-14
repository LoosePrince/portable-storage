package com.portable.storage.mixin.client;

import com.portable.storage.PortableStorage;
import com.portable.storage.client.ClientConfig;
import com.portable.storage.client.ui.StorageUIComponent;
// 统一后不再使用 RefillCraftingC2SPayload
import com.portable.storage.net.payload.RequestSyncC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import net.minecraft.item.Items;
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
    // 槽位贴图（用于绘制3x3覆盖槽位）
    @Unique private static final Identifier portableStorage$SLOT_TEX = Identifier.of("portable-storage", "textures/gui/slot.png");
    // 双击检测状态
    @Unique private long portableStorage$lastClickTs = 0L;
    @Unique private double portableStorage$lastClickX = 0;
    @Unique private double portableStorage$lastClickY = 0;

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

        // 标记开始查看仓库界面
        com.portable.storage.sync.PlayerViewState.startViewing(MinecraftClient.getInstance().player.getUuid());
        
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
            // 检查仓库是否已启用
            if (com.portable.storage.client.ClientStorageState.isStorageEnabled()) {
                // 渲染仓库UI（启用折叠功能）
                portableStorage$uiComponent.render(context, mouseX, mouseY, delta, x, y, backgroundWidth, backgroundHeight, true);
            }
            // 若启用工作台升级且配置允许，则在2x2合成区域上覆盖3x3槽位提示（严格根据实际槽位坐标对齐）
            if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && portableStorage$hasCraftingUpgradeClient() && com.portable.storage.client.ClientConfig.getInstance().virtualCraftingVisible) {
                MinecraftClient mc2 = MinecraftClient.getInstance();
                if (mc2 != null && mc2.player != null && mc2.player.currentScreenHandler instanceof net.minecraft.screen.PlayerScreenHandler handler) {
                    // PlayerScreenHandler: 0=输出, 1..4=2x2输入
                    if (handler.slots.size() > 4) {
                        int slotSize = 18;
                        // 顶左输入槽（索引1）作为3x3左上基准
                        int sx1 = x + handler.getSlot(1).x;
                        int sy1 = y + handler.getSlot(1).y;
                        // 3x3 覆盖左上与 2x2 顶左对齐，并向右下扩展
                        int overlayLeft = sx1;
                        int overlayTop = sy1;
                        int hoveredSlotIdx = -1;
                        for (int r = 0; r < 3; r++) {
                            for (int c = 0; c < 3; c++) {
                                int sxOverlay = overlayLeft + c * slotSize - 1;
                                int syOverlay = overlayTop + r * slotSize - 1;
                                context.drawTexture(portableStorage$SLOT_TEX, sxOverlay, syOverlay, 0, 0, 18, 18, 18, 18);
                                // 渲染虚拟物品（若已同步）
                                int idx = 1 + r * 3 + c;
                                net.minecraft.item.ItemStack vis = com.portable.storage.client.ui.VirtualCraftingOverlayState.get(idx);
                                if (!vis.isEmpty()) {
                                    context.drawItem(vis, sxOverlay + 1, syOverlay + 1);
                                    context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, vis, sxOverlay + 1, syOverlay + 1);
                                }
                                // 悬停检测 + 半透明遮罩
                                if (mouseX >= sxOverlay && mouseX < sxOverlay + 18 && mouseY >= syOverlay && mouseY < syOverlay + 18) {
                                    hoveredSlotIdx = idx;
                                    // 内缩1像素，不遮住槽位边框
                                    context.fill(sxOverlay + 1, syOverlay + 1, sxOverlay + 17, syOverlay + 17, 0x80FFFFFF);
                                }
                            }
                        }
                        // 结果槽提示（使用实际输出槽 index 0 的坐标）
                        int outX = x + handler.getSlot(0).x - 1;
                        int outY = y + handler.getSlot(0).y - 1;
                        context.drawTexture(portableStorage$SLOT_TEX, outX, outY, 0, 0, 18, 18, 18, 18);
                        net.minecraft.item.ItemStack resultVis = com.portable.storage.client.ui.VirtualCraftingOverlayState.get(0);
                        if (!resultVis.isEmpty()) {
                            context.drawItem(resultVis, outX + 1, outY + 1);
                            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, resultVis, outX + 1, outY + 1);
                        }
                        if (mouseX >= outX && mouseX < outX + 18 && mouseY >= outY && mouseY < outY + 18) {
                            hoveredSlotIdx = 0;
                            // 内缩1像素，不遮住槽位边框
                            context.fill(outX + 1, outY + 1, outX + 17, outY + 17, 0x80FFFFFF);
                        }
                        // 悬停工具提示
                        if (hoveredSlotIdx >= 0) {
                            net.minecraft.item.ItemStack tip = com.portable.storage.client.ui.VirtualCraftingOverlayState.get(hoveredSlotIdx);
                            if (!tip.isEmpty()) {
                                context.drawItemTooltip(MinecraftClient.getInstance().textRenderer, tip, mouseX, mouseY);
                            }
                        }
                    }
                }
            }
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

        // 覆盖层虚拟3x3交互：将点击转发给服务端进行合成/取出
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && portableStorage$hasCraftingUpgradeClient() && com.portable.storage.client.ClientConfig.getInstance().virtualCraftingVisible) {
            InventoryScreen self2 = (InventoryScreen)(Object)this;
            int screenWidth2 = self2.width;
            int screenHeight2 = self2.height;
            int backgroundWidth2 = 176;
            int backgroundHeight2 = 166;
            int x2 = (screenWidth2 - backgroundWidth2) / 2;
            int y2 = (screenHeight2 - backgroundHeight2) / 2;

            MinecraftClient mc2 = MinecraftClient.getInstance();
            if (mc2 != null && mc2.player != null && mc2.player.currentScreenHandler instanceof net.minecraft.screen.PlayerScreenHandler handler) {
                if (handler.slots.size() > 4) {
                    int slotSize = 18;
                    int sx1 = x2 + handler.getSlot(1).x;
                    int sy1 = y2 + handler.getSlot(1).y;
                    int overlayLeft = sx1;
                    int overlayTop = sy1;
                    int overlayRight = overlayLeft + 3 * slotSize;
                    int overlayBottom = overlayTop + 3 * slotSize;
                    boolean shift = (mc2.options != null && mc2.options.sneakKey.isPressed()) || Screen.hasShiftDown();
                    // 双击判断（250ms 内、移动不超过6像素，且光标不为空）
                    boolean isDoubleClick = false;
                    long nowTs = System.currentTimeMillis();
                    if (nowTs - portableStorage$lastClickTs < 250 && Math.hypot(mouseX - portableStorage$lastClickX, mouseY - portableStorage$lastClickY) <= 6.0) {
                        ItemStack cursorNow = mc2.player.currentScreenHandler.getCursorStack();
                        isDoubleClick = !cursorNow.isEmpty();
                    }
                    portableStorage$lastClickTs = nowTs;
                    portableStorage$lastClickX = mouseX;
                    portableStorage$lastClickY = mouseY;

                    // 命中3x3输入区
                    if (mouseX >= overlayLeft && mouseX < overlayRight && mouseY >= overlayTop && mouseY < overlayBottom) {
                        // 双击合并到光标
                        if (isDoubleClick) {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.CraftingOverlayActionC2SPayload(
                                com.portable.storage.net.payload.CraftingOverlayActionC2SPayload.Action.DOUBLE_CLICK,
                                0, 0, false,
                                net.minecraft.item.ItemStack.EMPTY,
                                "",
                                null,
                                null
                            ));
                            cir.setReturnValue(true);
                            return;
                        }
                        int relX = (int)(mouseX - overlayLeft);
                        int relY = (int)(mouseY - overlayTop);
                        int col = Math.min(2, Math.max(0, relX / slotSize));
                        int row = Math.min(2, Math.max(0, relY / slotSize));
                        int slotIndex = 1 + row * 3 + col; // 1..9
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.CraftingOverlayActionC2SPayload(
                            com.portable.storage.net.payload.CraftingOverlayActionC2SPayload.Action.CLICK,
                            slotIndex, button, shift,
                            net.minecraft.item.ItemStack.EMPTY,
                            "",
                            null,
                            null
                        ));
                        cir.setReturnValue(true);
                        return;
                    }

                    // 命中结果槽（索引0，使用实际输出槽坐标范围）
                    int outX = x2 + handler.getSlot(0).x;
                    int outY = y2 + handler.getSlot(0).y;
                    if (mouseX >= outX && mouseX < outX + 18 && mouseY >= outY && mouseY < outY + 18) {
                        // 双击合并到光标
                        if (isDoubleClick) {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.CraftingOverlayActionC2SPayload(
                                com.portable.storage.net.payload.CraftingOverlayActionC2SPayload.Action.DOUBLE_CLICK,
                                0, 0, false,
                                net.minecraft.item.ItemStack.EMPTY,
                                "",
                                null,
                                null
                            ));
                            cir.setReturnValue(true);
                            return;
                        }
                        // 客户端前置校验：若光标已达最大或继续取会超过最大，则不发送取出
                        ItemStack resultVis = com.portable.storage.client.ui.VirtualCraftingOverlayState.get(0);
                        if (!resultVis.isEmpty()) {
                            ItemStack cursor = mc2.player.currentScreenHandler.getCursorStack();
                            if (!cursor.isEmpty() && !ItemStack.areItemsAndComponentsEqual(cursor, resultVis)) {
                                // 光标物品与合成结果不同，不取出
                                cir.setReturnValue(true);
                                return;
                            }
                            if (!cursor.isEmpty() && ItemStack.areItemsAndComponentsEqual(cursor, resultVis)) {
                                int maxPerStack = Math.min(resultVis.getMaxCount(), mc2.player.getInventory().getMaxCountPerStack());
                                int would = cursor.getCount() + resultVis.getCount();
                                if (cursor.getCount() >= maxPerStack || would > maxPerStack) {
                                    // 阻止继续取出，避免超过最大堆叠
                                    cir.setReturnValue(true);
                                    return;
                                }
                            }
                        }
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.portable.storage.net.payload.CraftingOverlayActionC2SPayload(
                            com.portable.storage.net.payload.CraftingOverlayActionC2SPayload.Action.CLICK,
                            0, button, shift,
                            net.minecraft.item.ItemStack.EMPTY,
                            "",
                            null,
                            null
                        ));
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }

        // 覆盖层激活时，阻断对原 2x2 槽位(1..4)的交互
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && portableStorage$hasCraftingUpgradeClient() && com.portable.storage.client.ClientConfig.getInstance().virtualCraftingVisible) {
            MinecraftClient mc3 = MinecraftClient.getInstance();
            if (mc3 != null && mc3.player != null && mc3.player.currentScreenHandler instanceof net.minecraft.screen.PlayerScreenHandler handler) {
                int screenX = ((InventoryScreen)(Object)this).width;
                int screenY = ((InventoryScreen)(Object)this).height;
                int backgroundWidth = 176;
                int backgroundHeight = 166;
                int x0 = (screenX - backgroundWidth) / 2;
                int y0 = (screenY - backgroundHeight) / 2;
                for (int i = 1; i <= 4 && i < handler.slots.size(); i++) {
                    int sx = x0 + handler.getSlot(i).x;
                    int sy = y0 + handler.getSlot(i).y;
                    if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }

        // 检查仓库是否已启用
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled()) {
            // 委托给UI组件处理（启用折叠功能）
            if (portableStorage$uiComponent.mouseClicked(mouseX, mouseY, button, true)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void portableStorage$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 检查仓库是否已启用
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled()) {
            // 检查鼠标是否在仓库UI区域内，如果是则阻止事件穿透
            if (portableStorage$uiComponent.isOverAnyComponent(mouseX, mouseY)) {
                cir.setReturnValue(true);
                return;
            }
        }

        // 在覆盖层启用时，阻断原 2x2 槽位与覆盖区域的释放事件，防止穿透
        if (!(com.portable.storage.client.ClientStorageState.isStorageEnabled() && portableStorage$hasCraftingUpgradeClient() && com.portable.storage.client.ClientConfig.getInstance().virtualCraftingVisible)) return;

        InventoryScreen self = (InventoryScreen)(Object)this;
        int screenWidth = self.width;
        int screenHeight = self.height;
        int backgroundWidth = 176;
        int backgroundHeight = 166;
        int x = (screenWidth - backgroundWidth) / 2;
        int y = (screenHeight - backgroundHeight) / 2;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || !(mc.player.currentScreenHandler instanceof net.minecraft.screen.PlayerScreenHandler handler)) return;
        if (handler.slots.size() <= 4) return;

        // 覆盖 3x3 区域
        int slotSize = 18;
        int sx1 = x + handler.getSlot(1).x;
        int sy1 = y + handler.getSlot(1).y;
        int overlayLeft = sx1;
        int overlayTop = sy1;
        int overlayRight = overlayLeft + 3 * slotSize;
        int overlayBottom = overlayTop + 3 * slotSize;
        if (mouseX >= overlayLeft && mouseX < overlayRight && mouseY >= overlayTop && mouseY < overlayBottom) {
            cir.setReturnValue(true);
            return;
        }
        // 结果槽
        int outX = x + handler.getSlot(0).x;
        int outY = y + handler.getSlot(0).y;
        if (mouseX >= outX && mouseX < outX + 18 && mouseY >= outY && mouseY < outY + 18) {
            cir.setReturnValue(true);
            return;
        }
        // 原 2x2 槽位(1..4)
        for (int i = 1; i <= 4 && i < handler.slots.size(); i++) {
            int sx = x + handler.getSlot(i).x;
            int sy = y + handler.getSlot(i).y;
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                cir.setReturnValue(true);
                return;
            }
        }
    }


    // 注意：InventoryScreen 可能没有 mouseScrolled 方法，我们通过其他方式处理滚轮事件

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void portableStorage$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && portableStorage$uiComponent.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void portableStorage$charTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (com.portable.storage.client.ClientStorageState.isStorageEnabled() && portableStorage$uiComponent.charTyped(chr, modifiers)) {
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
        ClientPlayNetworking.send(new com.portable.storage.net.payload.CraftingOverlayActionC2SPayload(
            com.portable.storage.net.payload.CraftingOverlayActionC2SPayload.Action.REFILL,
            slotIndex, 0, false,
            targetStack,
            "",
            null,
            null
        ));
    }

    @Unique
    private boolean portableStorage$hasCraftingUpgradeClient() {
        for (int i = 0; i < com.portable.storage.client.ClientUpgradeState.getSlotCount(); i++) {
            ItemStack st = com.portable.storage.client.ClientUpgradeState.getStack(i);
            if (!st.isEmpty() && st.getItem() == Items.CRAFTING_TABLE && !com.portable.storage.client.ClientUpgradeState.isSlotDisabled(i)) {
                return true;
            }
        }
        return false;
    }
}
