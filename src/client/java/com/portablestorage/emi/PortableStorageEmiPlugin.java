package com.portablestorage.emi;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.C2SRecipeTransferPayload;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.mixin.client.AbstractContainerScreenAccessor;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.widget.Bounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Collections;

public class PortableStorageEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // 1. Register Exclusion Areas
        registry.addExclusionArea(CraftingWarehouseScreen.class, (screen, consumer) -> {
            consumer.accept(new Bounds(screen.getWarehouseX(), screen.getWarehouseY(), screen.getWarehouseWidth(),
                    screen.getWarehouseHeight()));
        });

        registry.addExclusionArea(InventoryScreen.class, (screen, consumer) -> {
            Player player = Minecraft.getInstance().player;
            if (player != null && !player.getAbilities().instabuild) {
                PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
                if (warehouse.isEnabled()) {
                    AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
                    int x = accessor.portablestorage$getLeftPos() + WarehouseConstants.getWarehouseXOffset();
                    int y = accessor.portablestorage$getTopPos()
                            + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
                    int width = WarehouseConstants.getWarehouseWidth();
                    int height = warehouse.isFolded() ? WarehouseConstants.WAREHOUSE_FOLDED_HEIGHT
                            : WarehouseConstants.WAREHOUSE_TITLE_HEIGHT
                                    + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;
                    consumer.accept(new Bounds(x, y, width, height));
                }
            }
        });

        // 2. Register Recipe Handlers
        registry.addRecipeHandler(MenuType.CRAFTING, new WarehouseEmiRecipeHandler<>());
        registry.addRecipeHandler(ModScreenHandlers.CRAFTING_WAREHOUSE, new WarehouseEmiRecipeHandler<>());
    }

    private static class WarehouseEmiRecipeHandler<T extends AbstractContainerMenu> implements EmiRecipeHandler<T> {
        @Override
        public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
            return new EmiPlayerInventory(Collections.emptyList());
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING && recipe.getId() != null;
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
            return true;
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
            if (recipe.getId() != null) {
                ClientPlayNetworking.send(new C2SRecipeTransferPayload(recipe.getId(), context.getAmount() > 1));
                return true;
            }
            return false;
        }
    }
}
