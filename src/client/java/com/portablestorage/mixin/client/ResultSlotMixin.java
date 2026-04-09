package com.portablestorage.mixin.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.portablestorage.component.ModComponents;
import com.portablestorage.network.RefillPayload;
import com.portablestorage.screen.CraftingWarehouseScreenHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Unique
    private final Map<Integer, ItemStack> portablestorage$beforeCraftStacks = new HashMap<>();

    private static boolean portablestorage$isCraftingRefillMenu(Player player) {
        return player.containerMenu instanceof AbstractCraftingMenu
                || player.containerMenu instanceof CraftingWarehouseScreenHandler;
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void portablestorage$captureBeforeCraft(Player player, ItemStack carried, CallbackInfo ci) {
        if (!portablestorage$isCraftingRefillMenu(player)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player != player) {
            return;
        }

        var warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
        if (!warehouse.isEnabled() || !warehouse.isCraftRefill()) {
            return;
        }

        this.portablestorage$beforeCraftStacks.clear();
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == this.craftSlots) {
                this.portablestorage$beforeCraftStacks.put(slot.index, slot.getItem().copy());
            }
        }
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void portablestorage$refillAfterCraft(Player player, ItemStack carried, CallbackInfo ci) {
        if (!portablestorage$isCraftingRefillMenu(player)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player != player) {
            return;
        }
        if (this.portablestorage$beforeCraftStacks.isEmpty()) {
            return;
        }

        Map<ItemStack, List<Integer>> refills = new HashMap<>();
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != this.craftSlots) {
                continue;
            }
            ItemStack before = this.portablestorage$beforeCraftStacks.get(slot.index);
            if (before == null || before.isEmpty()) {
                continue;
            }

            ItemStack after = slot.getItem();
            if (after.isEmpty() || (ItemStack.isSameItemSameComponents(after, before) && after.getCount() < before.getCount())) {
                boolean merged = false;
                for (ItemStack key : refills.keySet()) {
                    if (ItemStack.isSameItemSameComponents(key, before)) {
                        refills.get(key).add(slot.index);
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    List<Integer> slotIds = new ArrayList<>();
                    slotIds.add(slot.index);
                    refills.put(before.copy(), slotIds);
                }
            }
        }

        for (var entry : refills.entrySet()) {
            ClientPlayNetworking.send(new RefillPayload(entry.getValue(), entry.getKey().copy()));
        }
        this.portablestorage$beforeCraftStacks.clear();
    }
}
