package com.portable.storage.client.emi;

import com.portable.storage.client.screen.PortableCraftingScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;

/**
 * Portable Storage 的 EMI 插件
 * 为自定义工作台界面提供 EMI 支持
 * 并在背包及其它容器上注册排除区域
 */
@EmiEntrypoint
public class PortableStorageEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // 注册工作台作为 EMI 工作站
        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(Items.CRAFTING_TABLE));
        
        // 绑定到自定义的 ScreenHandlerType，确保走我们的处理器
        registry.addRecipeHandler(com.portable.storage.PortableStorage.PORTABLE_CRAFTING_HANDLER, new PortableStorageEmiTransferHandler());
        
        // 添加通用堆叠提供器，让 EMI 能够从仓库槽位获取物品
        registry.addGenericStackProvider((screen, x, y) -> {
            if (screen instanceof PortableCraftingScreen portableScreen) {
                // 检查鼠标是否在仓库区域
                if (portableScreen.isMouseOverStorageArea(x, y)) {
                    // 获取鼠标下的物品
                    var item = portableScreen.getStorageItemUnderMouse(x, y);
                    if (item != null && !item.isEmpty()) {
                        return new EmiStackInteraction(EmiStack.of(item), null, false);
                    }
                }
            }
            return EmiStackInteraction.EMPTY;
        });
        
        // 添加排除区域，避免 EMI 界面与我们的 UI 重叠
        registry.addGenericExclusionArea((screen, consumer) -> {
            if (screen instanceof PortableCraftingScreen portableScreen) {
                portableScreen.getExclusionAreas(consumer);
            } else if (screen instanceof InventoryScreen invScreen) {
                boolean storageOnTop = (com.portable.storage.client.ClientConfig.getInstance().storagePos == com.portable.storage.client.ClientConfig.StoragePos.TOP);
                // 背包：包含折叠按钮、包含搜索位置设置、不包含切换原版按钮
                PortableStorageExclusionHelper.addAreasForScreen(
                    consumer,
                    (invScreen.width - 176) / 2,
                    (invScreen.height - 166) / 2,
                    176,
                    166,
                    storageOnTop,
                    true,
                    true,
                    false
                );
            } else if (screen instanceof HasPortableStorageExclusionAreas hasAreas) {
                // 其它容器：由实现接口的屏幕提供排除区域
                hasAreas.getPortableStorageExclusionAreas(consumer);
            }
        });
    }
}
