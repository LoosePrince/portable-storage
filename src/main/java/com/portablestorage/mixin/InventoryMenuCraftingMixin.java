package com.portablestorage.mixin;

import java.util.Optional;

import com.portablestorage.handler.WarehouseMenuHandler;
import com.portablestorage.mixin.accessor.InventoryMenuAccessor;
import com.portablestorage.util.FakePlayerUtils;
import com.portablestorage.util.InventoryMenuHelper;
import com.portablestorage.util.WarehouseUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InventoryMenu.class, priority = 500)
public abstract class InventoryMenuCraftingMixin extends AbstractContainerMenu {

    @Shadow public abstract CraftingContainer getCraftSlots();

    protected InventoryMenuCraftingMixin(MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void captureOwnerHead(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        InventoryMenuHelper.CURRENT_INVENTORY_OWNER.set(owner);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addExtraCraftingSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        try {
            if (FakePlayerUtils.isFakePlayer(owner))
                return;
            WarehouseMenuHandler.injectCraftingSlots(this, this.getCraftSlots(), owner);
        } finally {
            InventoryMenuHelper.CURRENT_INVENTORY_OWNER.remove();
        }
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void onSlotsChanged(Container container, CallbackInfo ci) {
        if (container != this.getCraftSlots()) {
            return;
        }

        Player owner = ((InventoryMenuAccessor) this).portablestorage$getOwner();
        if (owner != null && WarehouseUtils.is3x3Enabled(owner)) {
            if (!owner.level().isClientSide() && owner instanceof ServerPlayer serverPlayer) {
                CraftingInput craftingInput = this.getCraftSlots().asCraftInput();
                Optional<RecipeHolder<CraftingRecipe>> optional = serverPlayer.level().getServer().getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, craftingInput, serverPlayer.level());
                ItemStack itemStack = ItemStack.EMPTY;
                if (optional.isPresent()) {
                    RecipeHolder<CraftingRecipe> recipeHolder = optional.get();
                    itemStack = recipeHolder.value().assemble(craftingInput);
                }
                if (!this.slots.isEmpty() && this.slots.get(0) != null) {
                    this.slots.get(0).container.setItem(0, itemStack);
                }
                this.broadcastChanges();
                ci.cancel();
            }
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void handleCraftingQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (FakePlayerUtils.isFakePlayer(player))
            return;
        ItemStack result = WarehouseMenuHandler.handleCraftingQuickMove(this, this.slots, this.getCraftSlots(), player, index);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}