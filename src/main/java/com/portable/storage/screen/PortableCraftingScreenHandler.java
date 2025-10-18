package com.portable.storage.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.world.World;

public class PortableCraftingScreenHandler extends ScreenHandler {
    private final CraftingInventory input;
    private final CraftingResultInventory result;
    private final ScreenHandlerContext context;

    public PortableCraftingScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public PortableCraftingScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(com.portable.storage.PortableStorage.PORTABLE_CRAFTING_HANDLER, syncId);
        this.context = context;
        this.input = new CraftingInventory(this, 3, 3);
        this.result = new CraftingResultInventory();

        // 结果槽位 (index 0)
        this.addSlot(new CraftingResultSlot(playerInventory.player, this.input, this.result, 0, 124, 35));

        // 3x3 输入槽位 (index 1..9)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                this.addSlot(new Slot(this.input, j + i * 3, 30 + j * 18, 17 + i * 18));
            }
        }

        // 玩家物品栏 (index 10..36) 与快捷栏 (37..45)
        for (int m = 0; m < 3; ++m) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 84 + m * 18));
            }
        }

        for (int m = 0; m < 9; ++m) {
            this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 142));
        }

        this.onContentChanged(this.input);
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        this.context.run((world, pos) -> updateResult(world));
    }

    private void updateResult(World world) {
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>(this.input.size());
        for (int i = 0; i < this.input.size(); i++) {
            stacks.add(this.input.getStack(i).copy());
        }
        CraftingRecipeInput inputWrapper = CraftingRecipeInput.create(3, 3, stacks);
        RecipeEntry<?> recipeEntry = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, inputWrapper, world).orElse(null);
        if (recipeEntry != null && recipeEntry.value() instanceof net.minecraft.recipe.CraftingRecipe recipe) {
            ItemStack out = recipe.craft(inputWrapper, world.getRegistryManager());
            this.result.setStack(0, out);
        } else {
            this.result.setStack(0, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stackInSlot = slot.getStack();
            itemStack = stackInSlot.copy();
            if (index == 0) { // 结果槽
                if (!this.insertItem(stackInSlot, 10, 46, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickTransfer(stackInSlot, itemStack);
            } else if (index >= 10 && index < 46) { // 玩家背包
                if (!this.insertItem(stackInSlot, 1, 10, false)) {
                    // 背包 <-> 快捷栏
                    if (index < 37) {
                        if (!this.insertItem(stackInSlot, 37, 46, false)) return ItemStack.EMPTY;
                    } else if (!this.insertItem(stackInSlot, 10, 37, false)) return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(stackInSlot, 10, 46, false)) { // 合成格 -> 背包
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();

            if (stackInSlot.getCount() == itemStack.getCount()) return ItemStack.EMPTY;
            slot.onTakeItem(player, stackInSlot);
        }
        return itemStack;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // 关闭时将输入格 (1..9) 安全返还给玩家，避免物品消失
        for (int i = 1; i <= 9; i++) {
            if (i >= this.slots.size()) break;
            Slot slot = this.getSlot(i);
            ItemStack st = slot.getStack();
            if (!st.isEmpty()) {
                ItemStack copy = st.copy();
                slot.setStack(ItemStack.EMPTY);
                // 尝试放入玩家背包或掉落到地上
                if (!player.getInventory().insertStack(copy)) {
                    player.dropItem(copy, false);
                }
            }
        }
        this.sendContentUpdates();
    }

    public CraftingInventory getCraftingInventory() { return input; }
    public CraftingResultInventory getResultInventory() { return result; }

    // 供服务端切回原版界面时读取原始方块上下文
    public ScreenHandlerContext getContext() { return context; }
}


