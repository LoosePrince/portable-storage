package com.portablestorage.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class MyWarehouseComponent implements WarehouseComponent {
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(54, ItemStack.EMPTY);
    private final Object provider;

    public MyWarehouseComponent(Object provider) {
        this.provider = provider;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.loadAllItems(tag, inventory, registries);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, inventory, registries);
    }

    // Container methods
    @Override
    public int getContainerSize() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(inventory, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public void setChanged() {
        if (provider instanceof Player player) {
            ModComponents.WAREHOUSE.sync(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        inventory.clear();
        this.setChanged();
    }
}
