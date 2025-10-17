package com.portable.storage.mixin.client;

import com.portable.storage.client.ClientContainerDisplayConfig;
import com.portable.storage.client.ClientStorageState;
import com.portable.storage.client.emi.HasPortableStorageExclusionAreas;
import com.portable.storage.client.ui.StorageUIComponent;
import com.portable.storage.util.ContainerTypeDetector;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Consumer;

/**
 * 通用容器界面Mixin
 * 用于在配置的容器界面显示仓库
 */
@Mixin(HandledScreen.class)
public abstract class GenericContainerScreenMixin implements HasPortableStorageExclusionAreas {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;
    
    @Unique
    private StorageUIComponent portableStorage$uiComponent;
    
    @Inject(method = "init", at = @At("TAIL"))
    private void portableStorage$init(CallbackInfo ci) {
        // 初始化仓库UI组件
        if (portableStorage$uiComponent == null) {
            portableStorage$uiComponent = new StorageUIComponent();
        }
    }
    
    @Inject(method = "render", at = @At("TAIL"))
    private void portableStorage$renderOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 跳过背包界面和工作台界面，它们有专门的mixin处理
        if (((HandledScreen<?>)(Object)this) instanceof InventoryScreen ||
            ((HandledScreen<?>)(Object)this) instanceof CraftingScreen) {
            return;
        }
        
        // 检查是否应该在此容器显示仓库
        if (!portableStorage$shouldShowStorageInContainer()) {
            return;
        }
        
        // 渲染仓库UI
        if (portableStorage$uiComponent != null) {
            portableStorage$uiComponent.render(context, mouseX, mouseY, delta, x, y, backgroundWidth, backgroundHeight);
        }
    }
    
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void portableStorage$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 跳过背包界面和工作台界面，它们有专门的mixin处理
        if (((HandledScreen<?>)(Object)this) instanceof InventoryScreen ||
            ((HandledScreen<?>)(Object)this) instanceof CraftingScreen) {
            return;
        }
        
        // 检查是否应该在此容器显示仓库
        if (!portableStorage$shouldShowStorageInContainer()) {
            return;
        }
        
        // 处理仓库UI的鼠标点击
        if (portableStorage$uiComponent != null && portableStorage$uiComponent.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void portableStorage$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // 跳过背包界面和工作台界面，它们有专门的mixin处理
        if (((HandledScreen<?>)(Object)this) instanceof InventoryScreen ||
            ((HandledScreen<?>)(Object)this) instanceof CraftingScreen) {
            return;
        }
        
        // 检查是否应该在此容器显示仓库
        if (!portableStorage$shouldShowStorageInContainer()) {
            return;
        }
        
        // 检查鼠标是否在仓库UI区域内，如果是则阻止事件穿透
        if (portableStorage$uiComponent != null && portableStorage$uiComponent.isOverAnyComponent(mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }
    
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void portableStorage$mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        // 跳过背包界面和工作台界面，它们有专门的mixin处理
        if (((HandledScreen<?>)(Object)this) instanceof InventoryScreen ||
            ((HandledScreen<?>)(Object)this) instanceof CraftingScreen) {
            return;
        }
        
        // 检查是否应该在此容器显示仓库
        if (!portableStorage$shouldShowStorageInContainer()) {
            return;
        }
        
        // 检查鼠标是否在仓库UI区域内，如果是则阻止事件穿透
        if (portableStorage$uiComponent != null && portableStorage$uiComponent.isOverAnyComponent(mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }
    
    @Unique
    private boolean portableStorage$shouldShowStorageInContainer() {
        // 检查仓库是否已启用
        if (!ClientStorageState.isStorageEnabled()) {
            return false;
        }
        
        // 检查是否有工作台升级
        if (!portableStorage$hasCraftingTableUpgrade()) {
            return false;
        }
        
        // 获取当前容器类型
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        
        var handler = client.player.currentScreenHandler;
        if (handler == null) {
            return false;
        }
        
        String containerId = ContainerTypeDetector.getContainerId(handler);
        if (containerId == null) {
            return false;
        }
        
        // 检查是否为支持的容器类型
        if (!ContainerTypeDetector.isSupportedContainer(containerId)) {
            return false;
        }
        
        // 检查配置是否允许在此容器显示仓库
        return ClientContainerDisplayConfig.getInstance().shouldShowStorageInContainer(containerId);
    }
    
    @Unique
    private boolean portableStorage$hasCraftingTableUpgrade() {
        // 检查所有升级槽位是否有工作台且未禁用
        for (int i = 0; i < 5; i++) {
            var stack = com.portable.storage.client.ClientUpgradeState.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.item.Items.CRAFTING_TABLE && !com.portable.storage.client.ClientUpgradeState.isSlotDisabled(i)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void getPortableStorageExclusionAreas(Consumer<Bounds> consumer) {
        if (!portableStorage$shouldShowStorageInContainer()) return;
        // 通用容器：仓库位于底部，不显示折叠按钮/搜索位置/切换按钮
        com.portable.storage.client.emi.PortableStorageExclusionHelper.addAreasForScreen(
            consumer, this.x, this.y, this.backgroundWidth, this.backgroundHeight,
            false,
            false,
            false,
            false
        );
    }
}
