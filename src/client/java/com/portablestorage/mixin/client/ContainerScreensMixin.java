package com.portablestorage.mixin.client;

import com.portablestorage.client.gui.WarehouseScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为多个具体的容器界面提供仓库渲染支持
 * 由于 AbstractContainerScreen 的 renderBg 是抽象方法，需要针对具体实现类进行注入
 * 使用字符串形式的类名，避免编译时找不到类的问题
 * 注意：排除 InventoryScreen，因为它由 InventoryScreenMixin 处理
 * 排除 CraftingWarehouseScreen，因为它有自己的实现
 */
@Mixin(targets = {
        // 具体容器界面类（有 renderBg 方法）
        "net.minecraft.client.gui.screens.inventory.ContainerScreen", // 箱子界面（不是 ChestScreen）
        "net.minecraft.client.gui.screens.inventory.HopperScreen",
        "net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen",
        "net.minecraft.client.gui.screens.inventory.DispenserScreen",
        "net.minecraft.client.gui.screens.inventory.BrewingStandScreen",
        "net.minecraft.client.gui.screens.inventory.BeaconScreen",
        "net.minecraft.client.gui.screens.inventory.EnchantmentScreen",
        "net.minecraft.client.gui.screens.inventory.LoomScreen",
        "net.minecraft.client.gui.screens.inventory.CartographyTableScreen",
        "net.minecraft.client.gui.screens.inventory.StonecutterScreen",
        "com.portablestorage.screen.BoundBarrelScreen",
        // 抽象基类（它们的子类也会被覆盖）
        "net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen", // 覆盖
                                                                            // FurnaceScreen、BlastFurnaceScreen、SmokerScreen
        "net.minecraft.client.gui.screens.inventory.ItemCombinerScreen" // 覆盖
                                                                        // AnvilScreen、GrindstoneScreen、SmithingScreen
})
public abstract class ContainerScreensMixin {

    /**
     * 在 renderBg 之后注入，绘制仓库背景（在原版槽位高亮之前）
     * 注意：排除 InventoryScreen 和 CraftingWarehouseScreen
     */
    @Inject(method = "renderBg", at = @At("RETURN"))
    private void onRenderBgReturn(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        // 排除 InventoryScreen，因为它由 InventoryScreenMixin 处理
        if ((Object) this instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
            return;
        // 排除 CraftingWarehouseScreen，因为它有自己的实现
        if ((Object) this instanceof com.portablestorage.screen.CraftingWarehouseScreen)
            return;

        WarehouseScreen screen = (WarehouseScreen) this;
        var warehouseWidget = screen.portablestorage$getWarehouseWidget();
        if (warehouseWidget != null && warehouseWidget.shouldShow()) {
            warehouseWidget.renderBackground(graphics, mouseX, mouseY);
        }
    }

}
