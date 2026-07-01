package com.portablestorage.screen;

import java.util.Optional;

import com.portablestorage.component.ModComponents;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.util.WarehouseConstants;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

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

        // 工作台结果槽位（索引 0）
        this.addSlot(new ResultSlot(playerInventory.player, this.craftSlots, this.resultSlots, 0, 124, 35));

        // 3x3 合成槽位（索引 1-9）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }

        // 玩家背包（索引 10-36）
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // 玩家快捷栏（索引 37-45）
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // 升级槽位
        PlayerWarehouse warehouse = ModComponents.getWarehouse(player);
        int upgradeX = WarehouseConstants.getWarehouseXOffset() + WarehouseConstants.UPGRADE_SLOT_RELATIVE_X;
        int upgradeYBase = WarehouseConstants.getWarehouseYOffset(warehouse.getVisibleRows())
                + WarehouseConstants.UPGRADE_SLOT_RELATIVE_Y;
        for (int i = 0; i < WarehouseConstants.MAX_ROWS; i++) {
            this.addSlot(new com.portablestorage.upgrade.UpgradeSlot(warehouse, i, upgradeX,
                    upgradeYBase + i * WarehouseConstants.SLOT_SIZE));
        }

        // 仓库槽位（索引 46+）
        int startX = WarehouseConstants.getSlotLogicX();
        int startY = WarehouseConstants.getSlotLogicY(warehouse.getVisibleRows());

        for (int row = 0; row < WarehouseConstants.MAX_ROWS; row++) {
            final int currentRow = row;
            for (int col = 0; col < WarehouseConstants.SLOTS_PER_ROW; col++) {
                this.addSlot(new Slot(warehouse, col + row * WarehouseConstants.SLOTS_PER_ROW,
                        startX + col * WarehouseConstants.SLOT_SIZE, startY + row * WarehouseConstants.SLOT_SIZE) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return true;
                    }

                    @Override
                    public boolean isActive() {
                        // 在专门的合成界面中，不需要判定创造模式，始终允许使用
                        return !warehouse.isFolded() && warehouse.isEnabled()
                                && currentRow < warehouse.getVisibleRows();
                    }
                });
            }
        }
    }

    protected static void updateResult(AbstractContainerMenu menu, Level level, Player player,
            CraftingContainer craftSlots, ResultContainer resultSlots) {
        if (!level.isClientSide()) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            ItemStack itemStack = ItemStack.EMPTY;
            CraftingInput craftingInput = craftSlots.asCraftInput();
            Optional<RecipeHolder<CraftingRecipe>> optional = level.getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, craftingInput, level);
            if (optional.isPresent()) {
                RecipeHolder<CraftingRecipe> recipeHolder = optional.get();
                if (resultSlots.setRecipeUsed(serverPlayer, recipeHolder)) {
                    itemStack = recipeHolder.value().assemble(craftingInput);
                }
            }

            resultSlots.setItem(0, itemStack);
            menu.setRemoteSlot(0, itemStack);
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                    menu.containerId, menu.incrementStateId(), 0, itemStack));
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
        PlayerWarehouse warehouse = ModComponents.getWarehouse(player);
        return warehouse.hasWorkbenchUpgrade();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();

            // 动态识别各部分槽位范围
            int invStart = -1, invEnd = -1;
            int hotbarStart = -1, hotbarEnd = -1;
            for (int i = 0; i < this.slots.size(); i++) {
                Slot s = this.slots.get(i);
                if (s.container instanceof Inventory) {
                    int containerSlot = s.getContainerSlot();
                    if (containerSlot >= 9 && containerSlot < 36) {
                        if (invStart == -1)
                            invStart = i;
                        invEnd = i + 1;
                    } else if (containerSlot >= 0 && containerSlot < 9) {
                        if (hotbarStart == -1)
                            hotbarStart = i;
                        hotbarEnd = i + 1;
                    }
                }
            }
            int totalInvStart = Math.min(invStart, hotbarStart);
            int totalInvEnd = Math.max(invEnd, hotbarEnd);

            // 获取仓库实例用于后续判定
            PlayerWarehouse warehouse = ModComponents.getWarehouse(player);

            if (slot instanceof ResultSlot) { // 合成结果：使用原版逻辑处理
                // 原版逻辑：循环处理直到无法移动或槽位为空
                while (slot.hasItem()) {
                    ItemStack currentResult = slot.getItem();
                    ItemStack resultCopy = currentResult.copy();

                    // 触发物品的合成事件
                    currentResult.getItem().onCraftedBy(currentResult, player);

                    // 尝试移动到玩家背包或快捷栏
                    if (!this.moveItemStackTo(currentResult, totalInvStart, totalInvEnd, true)) {
                        break;
                    }

                    // 触发快速合成事件
                    slot.onQuickCraft(currentResult, resultCopy);

                    // 消耗材料并刷新合成结果（关键：onTake会消耗材料）
                    slot.onTake(player, currentResult);

                    // 如果数量没有变化，说明移动失败，退出循环
                    if (currentResult.getCount() == resultCopy.getCount()) {
                        break;
                    }
                }
                return ItemStack.EMPTY;
            } else if (slot.container == this.craftSlots) { // 合成槽位
                if (!this.moveItemStackTo(itemStack2, totalInvStart, totalInvEnd, false)) {
                    return ItemStack.EMPTY;
                }
                this.slotsChanged(this.craftSlots); // 手动触发合成结果更新
            } else if (slot.container instanceof Inventory) { // 玩家背包或快捷栏
                // 尝试移动到仓库
                if (warehouse.isEnabled() && !warehouse.isFolded() && warehouse.isQuickInteraction()
                        && player instanceof ServerPlayer serverPlayer) {
                    WarehouseService.commitIfWarehouseChanged(serverPlayer, warehouse,
                            "crafting_warehouse.quick_move.to_warehouse", () -> {
                                ItemStack remaining = WarehouseManager.addFluid(warehouse, itemStack2, player,
                                        "crafting_warehouse.quick_move.to_warehouse");
                                slot.set(remaining);
                                return null;
                            });
                } else {
                    // 如果仓库不可用，尝试在背包和快捷栏之间移动
                    if (index >= invStart && index < invEnd) {
                        if (!this.moveItemStackTo(itemStack2, hotbarStart, hotbarEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= hotbarStart && index < hotbarEnd) {
                        if (!this.moveItemStackTo(itemStack2, invStart, invEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            } else if (slot.container == warehouse.upgradeContainer) { // 从升级槽位取出
                if (!this.moveItemStackTo(itemStack2, totalInvStart, totalInvEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slot.container instanceof PlayerWarehouse) { // 仓库槽位
                if (itemStack2.is(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE) ||
                        itemStack2.is(com.portablestorage.item.ModItems.VIRTUAL_LAVA) ||
                        itemStack2.is(com.portablestorage.item.ModItems.VIRTUAL_WATER) ||
                        itemStack2.is(com.portablestorage.item.ModItems.VIRTUAL_MILK)) {
                    return ItemStack.EMPTY;
                }

                if (warehouse.isQuickInteraction()) {
                    return ItemStack.EMPTY; // Shift+点击由 QuickTransferPayload 处理
                }

                // 尝试移动到玩家背包
                if (!this.moveItemStackTo(itemStack2, totalInvStart, totalInvEnd, true)) {
                    return ItemStack.EMPTY;
                }

                int movedCount = itemStack.getCount() - itemStack2.getCount();
                if (movedCount > 0 && player instanceof ServerPlayer serverPlayer) {
                    WarehouseService.commitIfWarehouseChanged(serverPlayer, (PlayerWarehouse) slot.container,
                            "crafting_warehouse.quick_move.from_warehouse", () -> {
                                ((PlayerWarehouse) slot.container).removeItem(slot.getContainerSlot(), movedCount);
                                return null;
                            });
                }
            }

            if (itemStack2.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemStack2);
        }

        return itemStack;
    }
}
