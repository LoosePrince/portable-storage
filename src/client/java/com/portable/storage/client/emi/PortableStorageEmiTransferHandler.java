package com.portable.storage.client.emi;

import com.portable.storage.client.ClientStorageState;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Portable Storage 工作台界面的 EMI 传输处理器
 * 让 EMI 能够识别仓库中的物品并填充配方
 */
public class PortableStorageEmiTransferHandler implements StandardRecipeHandler<com.portable.storage.screen.PortableCraftingScreenHandler> {
    private static final Logger LOG = LoggerFactory.getLogger("portable-storage/emi");

    @Override
    public List<Slot> getInputSources(com.portable.storage.screen.PortableCraftingScreenHandler handler) {
        // 返回空列表，因为我们使用自定义的库存系统
        return List.of();
    }

    @Override
    public List<Slot> getCraftingSlots(com.portable.storage.screen.PortableCraftingScreenHandler handler) {
        // 返回空列表，交由 EMI 自行处理
        return java.util.Collections.emptyList();
    }

    @Override
    public EmiPlayerInventory getInventory(net.minecraft.client.gui.screen.ingame.HandledScreen<com.portable.storage.screen.PortableCraftingScreenHandler> screen) {
        List<EmiStack> stacks = new ArrayList<>();
        
        // 添加玩家背包物品
        var handler = screen.getScreenHandler();
        for (int i = 10; i < handler.slots.size(); i++) { // 跳过合成槽位和快捷栏
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                stacks.add(EmiStack.of(stack));
            }
        }
        
        // 添加仓库中的物品
        var storageStacks = ClientStorageState.getStacks();
        int storageAdded = 0;
        for (int i = 0; i < storageStacks.size(); i++) {
            var item = storageStacks.get(i);
            if (!item.isEmpty()) {
                long count = ClientStorageState.getCount(i);
                stacks.add(EmiStack.of(item, (int) Math.min(Integer.MAX_VALUE, count)));
                storageAdded++;
            }
        }
        LOG.debug("EMI getInventory: playerStacks={}, storageStacks(nonEmpty)={}, totalStacks={}", stacks.size() - storageAdded, storageAdded, stacks.size());
        
        return new EmiPlayerInventory(stacks);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        // 支持所有合成配方
        return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING && recipe.supportsRecipeTree();
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<com.portable.storage.screen.PortableCraftingScreenHandler> context) {
        boolean ok = context.getInventory().canCraft(recipe);
        LOG.debug("EMI canCraft recipeId={} result={}", recipe.getId(), ok);
        return ok;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<com.portable.storage.screen.PortableCraftingScreenHandler> context) {
        var screen = context.getScreen();
        if (screen instanceof com.portable.storage.client.screen.PortableCraftingScreen portableScreen) {
            portableScreen.fillRecipeFromStorage(recipe);
            LOG.debug("EMI craft sent payload for recipeId={}", recipe.getId());
            net.minecraft.client.MinecraftClient.getInstance().setScreen(screen);
            return true;
        }
        return false;
    }

}
