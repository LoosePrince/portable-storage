package com.portable.storage.client.emi;

import com.portable.storage.client.ClientStorageState;
import com.portable.storage.client.screen.PortableCraftingScreen;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.MinecraftClient;
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
        LOG.info("EMI getInventory: playerStacks={}, storageStacks(nonEmpty)={}, totalStacks={}", stacks.size() - storageAdded, storageAdded, stacks.size());
        
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
        LOG.info("EMI canCraft recipeId={} result={}", recipe.getId(), ok);
        return ok;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<com.portable.storage.screen.PortableCraftingScreenHandler> context) {
        var screen = context.getScreen();
        if (screen instanceof com.portable.storage.client.screen.PortableCraftingScreen portableScreen) {
            portableScreen.fillRecipeFromStorage(recipe);
            LOG.info("EMI craft sent payload for recipeId={}", recipe.getId());
            net.minecraft.client.MinecraftClient.getInstance().setScreen(screen);
            return true;
        }
        return false;
    }

    /**
     * 处理配方填充
     */
    private boolean handleRecipe(EmiRecipe recipe, net.minecraft.client.gui.screen.ingame.HandledScreen<com.portable.storage.screen.PortableCraftingScreenHandler> screen, boolean simulate) {
        // 获取配方输入
        var inputs = recipe.getInputs();
        if (inputs.isEmpty()) {
            return false;
        }

        // 检查材料是否足够
        var storageStacks = ClientStorageState.getStacks();
        var playerInventory = MinecraftClient.getInstance().player.getInventory();
        
        // 为每个输入槽位查找合适的材料
        for (int i = 0; i < Math.min(inputs.size(), 9); i++) {
            var input = inputs.get(i);
            var emiStacks = input.getEmiStacks();
            
            boolean found = false;
            for (var emiStack : emiStacks) {
                ItemStack stack = emiStack.getItemStack();
                if (stack.isEmpty()) continue;
                
                // 检查玩家背包
                for (int j = 0; j < playerInventory.size(); j++) {
                    ItemStack invStack = playerInventory.getStack(j);
                    if (!invStack.isEmpty() && (ItemStack.areItemsEqual(invStack, stack) || ItemStack.areItemsAndComponentsEqual(invStack, stack))) {
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    // 检查仓库
                    for (int j = 0; j < storageStacks.size(); j++) {
                        ItemStack storageStack = storageStacks.get(j);
                        if (!storageStack.isEmpty() && (ItemStack.areItemsEqual(storageStack, stack) || ItemStack.areItemsAndComponentsEqual(storageStack, stack))) {
                            found = true;
                            break;
                        }
                    }
                }
                
                if (found) break;
            }
            
            if (!found) {
                LOG.info("EMI missing ingredient at slot {} for recipe {}", i, recipe.getId());
                return false; // 缺少材料
            }
        }
        
        if (!simulate) {
            // 发送配方填充请求到服务器
            if (screen instanceof PortableCraftingScreen portableScreen) {
                portableScreen.fillRecipeFromStorage(recipe);
            }
        }
        
        return true;
    }
}
