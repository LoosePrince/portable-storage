package com.portablestorage.jei;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.network.C2SRecipeTransferPayload;
import com.portablestorage.screen.CraftingWarehouseScreen;
import com.portablestorage.screen.CraftingWarehouseScreenHandler;
import com.portablestorage.screen.ModScreenHandlers;
import com.portablestorage.util.WarehouseConstants;
import com.portablestorage.mixin.client.AbstractContainerScreenAccessor;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@JeiPlugin
public class PortableStorageJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_ID = new ResourceLocation("portablestorage", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // 仓库合成台（3x3）
        registration.addRecipeTransferHandler(new WarehouseTransferHandler<>(CraftingWarehouseScreenHandler.class, ModScreenHandlers.CRAFTING_WAREHOUSE), RecipeTypes.CRAFTING);
        
        // 玩家背包（2x2）
        // InventoryMenu 没有标准的 MenuType，因此传递 null 并返回 Optional.empty()
        registration.addRecipeTransferHandler(new WarehouseTransferHandler<>(InventoryMenu.class, null), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // 仓库界面
        registration.addGuiContainerHandler(CraftingWarehouseScreen.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<CraftingWarehouseScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(CraftingWarehouseScreen containerScreen) {
                return Collections.singletonList(new Rect2i(
                    containerScreen.getWarehouseX(), 
                    containerScreen.getWarehouseY(), 
                    containerScreen.getWarehouseWidth(), 
                    containerScreen.getWarehouseHeight()
                ));
            }
        });

        // 原版背包界面 - 将仓库区域标记为额外区域
        registration.addGuiContainerHandler(InventoryScreen.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<InventoryScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                Player player = Minecraft.getInstance().player;
                if (player == null || player.getAbilities().instabuild) return Collections.emptyList();
                
                PlayerWarehouse warehouse = ModComponents.get(player).getWarehouse(player.getUUID());
                if (!warehouse.isEnabled()) return Collections.emptyList();

                AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
                int guiLeft = accessor.portablestorage$getLeftPos();
                int guiTop = accessor.portablestorage$getTopPos();

                int x = guiLeft + WarehouseConstants.getWarehouseXOffset();
                int y = guiTop + WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows());
                int width = WarehouseConstants.getWarehouseWidth();
                int height = warehouse.isFolded() ? WarehouseConstants.WAREHOUSE_FOLDED_HEIGHT : 
                             WarehouseConstants.WAREHOUSE_TITLE_HEIGHT + warehouse.getVisibleRows() * WarehouseConstants.SLOT_SIZE;

                return Collections.singletonList(new Rect2i(x, y, width, height));
            }
        });
    }

    private static class WarehouseTransferHandler<T extends net.minecraft.world.inventory.AbstractContainerMenu> implements IRecipeTransferHandler<T, CraftingRecipe> {
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
        public mezz.jei.api.recipe.RecipeType<CraftingRecipe> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        @Nullable
        public IRecipeTransferError transferRecipe(T container, CraftingRecipe recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxStack, boolean doTransfer) {
            if (doTransfer && player != null && player.level() != null && player.level().isClientSide) {
                ResourceLocation recipeId = null;
                // 在客户端，通过 RecipeManager 查找 recipe ID
                var clientLevel = (net.minecraft.client.multiplayer.ClientLevel) player.level();
                var recipeManager = clientLevel.getRecipeManager();
                // 使用反射访问 private recipes 字段
                try {
                    java.lang.reflect.Field recipesField = net.minecraft.world.item.crafting.RecipeManager.class.getDeclaredField("recipes");
                    recipesField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Map<net.minecraft.world.item.crafting.RecipeType<?>, java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>>> recipesMap = 
                        (java.util.Map<net.minecraft.world.item.crafting.RecipeType<?>, java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>>>) recipesField.get(recipeManager);
                    var craftingRecipes = recipesMap.get(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
                    if (craftingRecipes != null) {
                        for (var entry : craftingRecipes.entrySet()) {
                            if (entry.getValue() == recipe) {
                                recipeId = entry.getKey();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // 如果反射失败，无法获取 recipe ID，跳过
                    com.portablestorage.PortableStorage.LOGGER.warn("Failed to get recipe ID from JEI", e);
                }
                if (recipeId != null) {
                    ClientPlayNetworking.send(new C2SRecipeTransferPayload(recipeId, maxStack));
                }
            }
            return null;
        }
    }
}
