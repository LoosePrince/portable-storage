package com.portable.storage.client.emi;

import com.portable.storage.client.screen.PortableCraftingScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.item.Items;

/**
 * Portable Storage 的 EMI 插件
 * 为自定义工作台界面提供 EMI 支持
 */
@EmiEntrypoint
public class PortableStorageEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // 注册工作台作为 EMI 工作站
        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(Items.CRAFTING_TABLE));
        
        // 绑定到自定义的 ScreenHandlerType，确保走我们的处理器
        registry.addRecipeHandler(com.portable.storage.PortableStorage.PORTABLE_CRAFTING_HANDLER, new PortableStorageEmiTransferHandler());
        
        // 添加通用堆栈提供器，让 EMI 能够从仓库槽位获取物品
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
            }
        });
    }
}
