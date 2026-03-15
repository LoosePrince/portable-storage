package com.portablestorage.jei;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.portablestorage.PortableStorage;
import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.mixin.client.AbstractContainerScreenAccessor;
import com.portablestorage.network.C2SRecipeTransferPayload;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.CraftingWarehouseScreenHandler;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.util.WarehouseConstants;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public class PortableStorageJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID = PortableStorage.id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // 仓库合成台（3x3）
        registration.addRecipeTransferHandler(new WarehouseTransferHandler<>(CraftingWarehouseScreenHandler.class,
                ModScreenHandlers.CRAFTING_WAREHOUSE), RecipeTypes.CRAFTING);

        // 玩家背包（2x2）
        // InventoryMenu 没有标准的 MenuType，因此传递 null 并返回 Optional.empty()
        registration.addRecipeTransferHandler(new WarehouseTransferHandler<>(InventoryMenu.class, null),
                RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 仓库界面
        registration.addGuiContainerHandler(CraftingWarehouseScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<CraftingWarehouseScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(CraftingWarehouseScreen containerScreen) {
                        return Collections.singletonList(new Rect2i(
                                containerScreen.getWarehouseX(),
                                containerScreen.getWarehouseY(),
                                containerScreen.getWarehouseWidth(),
                                containerScreen.getWarehouseHeight()));
                    }
                });

        // 原版背包界面 - 将仓库区域标记为额外区域
        registration.addGuiContainerHandler(InventoryScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<InventoryScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                        Player player = Minecraft.getInstance().player;
                        if (player == null || player.getAbilities().instabuild)
                            return Collections.emptyList();

                        PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
                        if (!warehouse.isEnabled())
                            return Collections.emptyList();

                        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
                        int guiLeft = accessor.portablestorage$getLeftPos();
                        int guiTop = accessor.portablestorage$getTopPos();

                        int x = guiLeft + WarehouseConstants.getWarehouseXOffset();
                        int y = guiTop + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
                        int width = WarehouseConstants.getWarehouseWidth();
                        int height = warehouse.isFolded() ? WarehouseConstants.WAREHOUSE_FOLDED_HEIGHT
                                : WarehouseConstants.WAREHOUSE_TITLE_HEIGHT
                                        + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;

                        return Collections.singletonList(new Rect2i(x, y, width, height));
                    }
                });
    }

    private static class WarehouseTransferHandler<T extends net.minecraft.world.inventory.AbstractContainerMenu>
            implements IRecipeTransferHandler<T, RecipeHolder<CraftingRecipe>> {
        private final Class<T> containerClass;
        private final @Nullable MenuType<T> menuType;

        public WarehouseTransferHandler(Class<T> containerClass, @Nullable MenuType<T> menuType) {
            this.containerClass = containerClass;
            this.menuType = menuType;
        }

        @Override
        public Class<? extends T> getContainerClass() {
            return containerClass;
        }

        @Override
        public Optional<MenuType<T>> getMenuType() {
            return Optional.ofNullable(menuType);
        }

        @Override
        public mezz.jei.api.recipe.RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            @SuppressWarnings("unchecked")
            mezz.jei.api.recipe.RecipeType<RecipeHolder<CraftingRecipe>> type = (mezz.jei.api.recipe.RecipeType<RecipeHolder<CraftingRecipe>>) (Object) RecipeTypes.CRAFTING;
            return type;
        }

        @Override
        @Nullable
        public IRecipeTransferError transferRecipe(T container, RecipeHolder<CraftingRecipe> recipeHolder,
                IRecipeSlotsView recipeSlots, Player player, boolean maxStack, boolean doTransfer) {
            if (doTransfer) {
                String recipeId = recipeHolder.id().toString();
                if (recipeId.contains(" / ")) {
                    recipeId = recipeId.substring(recipeId.indexOf(" / ") + 3);
                }
                if (recipeId.endsWith("]")) {
                    recipeId = recipeId.substring(0, recipeId.length() - 1);
                }
                ClientPlayNetworking.send(new C2SRecipeTransferPayload(recipeId, maxStack));
            }
            return null;
        }
    }
}
