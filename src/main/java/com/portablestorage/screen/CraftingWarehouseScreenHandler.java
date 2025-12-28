package com.portablestorage.screen;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.util.WarehouseConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class CraftingWarehouseScreenHandler extends AbstractContainerMenu {
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;

    public CraftingWarehouseScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, ContainerLevelAccess.NULL);
    }

    public CraftingWarehouseScreenHandler(int syncId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModScreenHandlers.CRAFTING_WAREHOUSE, syncId);
        this.access = access;
        this.player = playerInventory.player;

        // 1. 工作台结果槽位 (Index 0)
        this.addSlot(new ResultSlot(playerInventory.player, this.craftSlots, this.resultSlots, 0, 124, 35));

        // 2. 3x3 合成槽位 (Index 1-9)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }

        // 3. 玩家背包 (Index 10-36)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // 4. 玩家快捷栏 (Index 37-45)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // 5. 仓库槽位 (Index 46+)
        PlayerWarehouse warehouse = ModComponents.WAREHOUSE.get(player.level()).getWarehouse(player.getUUID());
        int startX = WarehouseConstants.SLOT_LOGIC_X;
        int startY = WarehouseConstants.SLOT_LOGIC_Y_BASE;
        
        for (int row = 0; row < WarehouseConstants.MAX_ROWS; row++) {
            final int currentRow = row;
            for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                this.addSlot(new Slot(warehouse, col + row * WarehouseConstants.SLOTS_PER_ROW, startX + col * WarehouseConstants.SLOT_SIZE, startY + row * WarehouseConstants.SLOT_SIZE) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return true; }

                    @Override
                    public boolean isActive() {
                        return !player.getAbilities().instabuild && !warehouse.isFolded() && warehouse.isEnabled() && currentRow < warehouse.getVisibleRows();
                    }
                });
            }
        }
    }

    protected static void updateResult(AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots, ResultContainer resultSlots) {
        if (!level.isClientSide) {
            ServerPlayer serverPlayer = (ServerPlayer)player;
            ItemStack itemStack = ItemStack.EMPTY;
            CraftingInput craftingInput = craftSlots.asCraftInput();
            Optional<RecipeHolder<CraftingRecipe>> optional = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level);
            if (optional.isPresent()) {
                RecipeHolder<CraftingRecipe> recipeHolder = optional.get();
                if (resultSlots.setRecipeUsed(level, serverPlayer, recipeHolder)) {
                    itemStack = recipeHolder.value().assemble(craftingInput, level.registryAccess());
                }
            }

            resultSlots.setItem(0, itemStack);
            menu.setRemoteSlot(0, itemStack);
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, itemStack));
        }
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        this.access.execute((level, pos) -> {
            updateResult(this, level, this.player, this.craftSlots, this.resultSlots);
        });
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            this.clearContainer(player, this.craftSlots);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // 随身仓库始终有效
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 这里可以复用之前的快速移动逻辑，但由于槽位索引变化，需要仔细处理
        // 简单起见，可以先实现基础的
        return ItemStack.EMPTY;
    }
}

