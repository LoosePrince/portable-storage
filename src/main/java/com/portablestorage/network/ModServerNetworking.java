package com.portablestorage.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.portablestorage.storage.service.WarehouseService;
import com.portablestorage.component.PlayerWarehouse;
import com.portablestorage.config.ModConfig;
import com.portablestorage.logic.WarehouseManager;
import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import com.portablestorage.screen.CraftingWarehouseScreenHandler;
import com.portablestorage.upgrade.ExperienceUpgrade;
import com.portablestorage.util.WarehouseSetting;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ModServerNetworking {

    public static void handleClickWarehouseSlot(C2SClickWarehouseSlotPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled()) return;

            int containerSlot = payload.containerSlot();
            int button = payload.button();

            if (payload.isUpgradeSlot()) {
                WarehouseService.commitIfWarehouseChanged(player, warehouse, "warehouse_click.upgrade_slot", () -> {
                    Slot targetSlot = null;
                    for (Slot s : player.containerMenu.slots) {
                        if (s instanceof com.portablestorage.upgrade.UpgradeSlot && s.getContainerSlot() == containerSlot) {
                            targetSlot = s;
                            break;
                        }
                    }
                    if (targetSlot == null) return null;

                    ItemStack cursorStack = player.containerMenu.getCarried();
                    if (!cursorStack.isEmpty()) {
                        if (targetSlot.mayPlace(cursorStack)) {
                            ItemStack stackInSlot = targetSlot.getItem();
                            int maxPlace = targetSlot.getMaxStackSize();
                            if (stackInSlot.isEmpty()) {
                                int toPlace = Math.min(cursorStack.getCount(), maxPlace);
                                targetSlot.set(cursorStack.split(toPlace));
                            } else if (ItemStack.isSameItemSameComponents(stackInSlot, cursorStack)) {
                                int canAdd = Math.min(cursorStack.getCount(), maxPlace - stackInSlot.getCount());
                                if (canAdd > 0) {
                                    stackInSlot.grow(canAdd);
                                    cursorStack.shrink(canAdd);
                                    targetSlot.setChanged();
                                }
                            }
                        }
                    } else {
                        ItemStack taken = targetSlot.remove(targetSlot.getMaxStackSize());
                        player.containerMenu.setCarried(taken);
                    }
                    return null;
                });
            } else {
                ItemStack slotItem = warehouse.getItem(containerSlot);
                if (slotItem.is(com.portablestorage.item.ModItems.BOTTLED_EXPERIENCE)) {
                    WarehouseService.commitIfWarehouseChanged(player, warehouse, "warehouse_click.experience", () -> {
                        handleExperienceClick(warehouse, button, player);
                        return null;
                    });
                    return;
                }

                ItemStack cursorStack = player.containerMenu.getCarried();
                WarehouseService.commitIfWarehouseChanged(player, warehouse, "warehouse_click.main_slot", () -> {
                    if (!cursorStack.isEmpty()) {
                        ItemStack remaining = WarehouseManager.addFluid(warehouse, cursorStack, player, "warehouse_click.main_slot");
                        player.containerMenu.setCarried(remaining);
                    } else {
                        int amount = (button == 1) ? 1 : 64;
                        ItemStack taken = WarehouseManager.removeItem(warehouse, containerSlot, amount, false);
                        player.containerMenu.setCarried(taken);
                    }
                    return null;
                });
            }
        });
    }

    private static void handleExperienceClick(PlayerWarehouse warehouse, int button, ServerPlayer player) {
        if (player.level().isClientSide()) return;

        ItemStack upgradeStack = warehouse.getUpgrade(ExperienceUpgrade.ID);
        if (upgradeStack.isEmpty()) return;

        if (ExperienceUpgrade.isMaintaining(upgradeStack)) {
            player.sendSystemMessage(
                    Component.translatable("tooltip.portablestorage.experience.maintain_blocked")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        int levels = ExperienceUpgrade.getStep(upgradeStack);
        ItemStack cursorStack = player.containerMenu.getCarried();

        if (cursorStack.isEmpty()) {
            if (button == 1) { // Right Click: Deposit
                int levelsToMove = levels;
                long totalToStore = 0;
                for (int i = 0; i < levelsToMove; i++) {
                    int lvl = Math.max(0, player.experienceLevel - i);
                    totalToStore += (ExperienceUpgrade.getExperienceForLevel(lvl)
                            - ExperienceUpgrade.getExperienceForLevel(Math.max(0, lvl - 1)));
                }

                long currentProgressXp = Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
                totalToStore += currentProgressXp;

                if (totalToStore > 0) {
                    warehouse.addExperience(totalToStore);
                    ExperienceUpgrade.addExperience(player, (int) -totalToStore);
                }
            } else if (button == 0) { // Left Click: Withdraw
                long totalToTake = 0;
                for (int i = 0; i < levels; i++) {
                    int lvl = player.experienceLevel + i;
                    totalToTake += (ExperienceUpgrade.getExperienceForLevel(lvl + 1)
                            - ExperienceUpgrade.getExperienceForLevel(lvl));
                }

                long canTake = Math.min(totalToTake, warehouse.getExperience());
                if (canTake > 0) {
                    warehouse.addExperience(-canTake);
                    ExperienceUpgrade.addExperience(player, (int) canTake);
                }
            }
        } else if (cursorStack.is(Items.GLASS_BOTTLE) && button == 1) {
            int canConvert = (int) (warehouse.getExperience() / 11);
            int toConvert = Math.min(cursorStack.getCount(), canConvert);

            if (toConvert > 0) {
                warehouse.addExperience(-(long) toConvert * 11);
                ItemStack bottles = new ItemStack(Items.EXPERIENCE_BOTTLE, toConvert);
                cursorStack.shrink(toConvert);
                if (!player.getInventory().add(bottles)) {
                    player.drop(bottles, false);
                }
            }

            if (!cursorStack.isEmpty() && canConvert < cursorStack.getCount()) {
                WarehouseManager.addItem(warehouse, cursorStack);
            }
        }
    }

    public static void handleQuickTransfer(QuickTransferPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            int slotId = payload.slotId();
            boolean isWarehouseSlot = payload.isWarehouseSlot();
            boolean isUpgradeSlot = payload.isUpgradeSlot();

            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled() || !warehouse.isQuickInteraction() || warehouse.isFolded()) return;

            if (isUpgradeSlot) {
                WarehouseService.commitIfWarehouseChanged(player, warehouse, "warehouse_click.upgrade_quick_move", () -> {
                    Slot targetSlot = null;
                    for (Slot s : player.containerMenu.slots) {
                        if (s instanceof com.portablestorage.upgrade.UpgradeSlot && s.getContainerSlot() == slotId) {
                            targetSlot = s;
                            break;
                        }
                    }
                    if (targetSlot != null) {
                        ItemStack stackInSlot = targetSlot.getItem();
                        if (!stackInSlot.isEmpty()) {
                            if (((AbstractContainerMenuAccessor) player.containerMenu).invokeMoveItemStackTo(stackInSlot, 9, 45, true)) {
                                targetSlot.set(stackInSlot);
                            }
                        }
                    }
                    return null;
                });
            } else if (isWarehouseSlot) {
                ItemStack stackInSlot = warehouse.getItem(slotId);
                if (stackInSlot.isEmpty()) return;

                WarehouseService.commitIfWarehouseChanged(player, warehouse, "quick_transfer.from_warehouse", () -> {
                    WarehouseManager.tryTransferToInventory(warehouse, slotId, player);
                    return null;
                });
            } else {
                var inv = player.getInventory();
                if (slotId < 0 || slotId >= inv.getContainerSize()) return;
                ItemStack stack = inv.getItem(slotId);
                if (!stack.isEmpty()) {
                    WarehouseService.commitIfWarehouseChanged(player, warehouse, "quick_transfer.to_warehouse", () -> {
                        ItemStack remaining = WarehouseManager.addFluid(warehouse, stack, player);
                        inv.setItem(slotId, remaining);
                        return null;
                    });
                }
            }
        });
    }

    public static void handleDropWarehouseItem(C2SDropWarehouseItemPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled()) return;

            int containerSlot = payload.slotId();
            ItemStack stackInSlot = warehouse.getItem(containerSlot);
            if (!stackInSlot.isEmpty()) {
                if (WarehouseManager.isVirtualFluid(stackInSlot.getItem())) return;

                int toTake = payload.dropFullStack()
                        ? (int) Math.min(stackInSlot.getMaxStackSize(), warehouse.getRealCount(containerSlot))
                        : 1;
                ItemStack dropped = WarehouseService.commitIfWarehouseChanged(player, warehouse,
                        "drop_warehouse_item", () -> WarehouseManager.removeItem(warehouse, containerSlot, toTake, true));
                if (!dropped.isEmpty()) {
                    player.drop(dropped, true);
                }
            }
        });
    }

    public static void handleOpenCrafting(OpenCraftingPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.hasWorkbenchUpgrade())
                return;

            player.openMenu(new SimpleMenuProvider(
                    (syncId, inventory, p) -> new CraftingWarehouseScreenHandler(syncId, inventory,
                            ContainerLevelAccess.create(player.level(), player.blockPosition())),
                    Component.translatable("container.crafting")));
        });
    }

    public static void handleUpdateWarehouseState(C2SUpdateWarehouseStatePayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            boolean changed = false;

            changed |= payload.scrollDelta().map(delta -> applyScrollDelta(warehouse, delta)).orElse(false);
            changed |= payload.searchText().map(text -> applySearchText(warehouse, text)).orElse(false);
            changed |= payload.rowsDelta().map(delta -> applyRowsDelta(warehouse, delta)).orElse(false);
            changed |= payload.upgradeScrollDelta().map(delta -> applyUpgradeScrollDelta(warehouse, delta)).orElse(false);

            if (payload.settingId().isPresent() && payload.settingValue().isPresent()) {
                changed |= applySetting(warehouse, payload.settingId().get(), payload.settingValue().get());
            }

            if (changed) syncChanges(player);
        });
    }

    private static boolean applyScrollDelta(PlayerWarehouse warehouse, int delta) {
        int before = warehouse.getScrollOffset();
        warehouse.setScrollOffset(before - delta);
        return warehouse.getScrollOffset() != before;
    }

    private static boolean applySearchText(PlayerWarehouse warehouse, String text) {
        String normalized = text.toLowerCase();
        if (warehouse.getSearchText().equals(normalized)) return false;
        warehouse.setSearchText(text);
        return true;
    }

    private static boolean applyRowsDelta(PlayerWarehouse warehouse, int delta) {
        int before = warehouse.getVisibleRows();
        int target = Math.clamp(before + delta, 1, 12);
        if (target == before) return false;
        warehouse.setVisibleRows(before + delta);
        return true;
    }

    private static boolean applyUpgradeScrollDelta(PlayerWarehouse warehouse, int delta) {
        int before = warehouse.getUpgradeScrollOffset();
        warehouse.setUpgradeScrollOffset(before - delta);
        return warehouse.getUpgradeScrollOffset() != before;
    }

    private static boolean applySetting(PlayerWarehouse warehouse, int settingId, int value) {
        WarehouseSetting setting = WarehouseSetting.values()[settingId];
        switch (setting) {
            case FOLD -> {
                boolean before = warehouse.isFolded();
                if (before == (value == 1)) return false;
                warehouse.setFolded(value == 1);
                return warehouse.isFolded() != before;
            }
            case SORT_MODE -> {
                if (warehouse.getSortMode() == value) return false;
                warehouse.setSortMode(value);
                return true;
            }
            case SORT_ORDER -> {
                if (warehouse.isAscending() == (value == 1)) return false;
                warehouse.setAscending(value == 1);
                return true;
            }
            case QUICK_INTERACTION -> {
                if (warehouse.isQuickInteraction() == (value == 1)) return false;
                warehouse.setQuickInteraction(value == 1);
                return true;
            }
            case SMART_COLLAPSE -> {
                if (warehouse.isSmartCollapse() == (value == 1)) return false;
                warehouse.setSmartCollapse(value == 1);
                return true;
            }
            case CRAFT_REFILL -> {
                if (warehouse.isCraftRefill() == (value == 1)) return false;
                warehouse.setCraftRefill(value == 1);
                return true;
            }
        }
        return false;
    }

    public static void handleUpgradeInteraction(C2SUpgradeInteractionPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            com.portablestorage.upgrade.UpgradeType type = com.portablestorage.upgrade.UpgradeRegistry.get(payload.upgradeId());

            if (type != null && warehouse.isEnabled() && !warehouse.getUpgrade(payload.upgradeId()).isEmpty()) {
                WarehouseService.commitIfWarehouseChanged(player, warehouse, "upgrade_interaction", () -> {
                    if (payload.button() == 1) type.onRightClick(warehouse, player);
                    else if (payload.button() == 2) type.onMiddleClick(warehouse, player);
                    return null;
                });
            }
        });
    }

    public static void handleUpdateServerConfig(UpdateServerConfigPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            if (!ModConfig.allowHotReload) return;

            ServerPlayer player = context.player();
            if (!context.server().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) return;

            ModConfig.enable3x3Crafting = payload.enable3x3Crafting();
            ModConfig.dropStorageOnDeath = payload.dropStorageOnDeath();
            ModConfig.maxStorageTypes = payload.maxStorageTypes();
            ModConfig.maxItemStackSize = payload.maxItemStackSize();
            ModConfig.baseMaxStorageTypes = payload.baseMaxStorageTypes();
            ModConfig.baseMaxItemStackSize = payload.baseMaxItemStackSize();
            ModConfig.maxItemNbtSize = payload.maxItemNbtSize();
            ModConfig.unconditionalWarehouse = payload.unconditionalWarehouse();
            ModConfig.baseWarehouseActivationItem = payload.baseWarehouseActivationItem();
            ModConfig.fullWarehouseActivationItem = payload.fullWarehouseActivationItem();
            ModConfig.hopperRange = payload.hopperRange();
            ModConfig.hopperFrequency = payload.hopperFrequency();
            ModConfig.lavaInfiniteThreshold = payload.lavaInfiniteThreshold();
            ModConfig.waterInfiniteThreshold = payload.waterInfiniteThreshold();
            ModConfig.riftUpgradeItem = payload.riftUpgradeItem();
            ModConfig.riftChunkSize = payload.riftChunkSize();
            ModConfig.riftPlotSpacingChunks = payload.riftPlotSpacingChunks();
            ModConfig.riftFloorY = payload.riftFloorY();
            ModConfig.enableRiftForcedLoading = payload.enableRiftForcedLoading();
            ModConfig.riftForcedLoadingRange = payload.riftForcedLoadingRange();
            ModConfig.enableRiftAvatar = payload.enableRiftAvatar();
            ModConfig.enableRiftBorder = payload.enableRiftBorder();
            ModConfig.riftBorderWarningBlocks = payload.riftBorderWarningBlocks();
            ModConfig.enableConduitUpgrade = payload.enableConduitUpgrade();

            ModConfig.save();

            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                var warehouse = getWarehouse(p);
                com.portablestorage.world.SpaceRiftManager.updatePlotForcedLoading(p, warehouse, true);
                if (p.level().dimension().equals(com.portablestorage.world.SpaceRiftManager.DIMENSION_KEY)) {
                    warehouse.setRiftBorderResendTicks(40);
                }
            }

            SyncConfigPayload sync = new SyncConfigPayload(
                    ModConfig.enable3x3Crafting, ModConfig.dropStorageOnDeath, ModConfig.allowHotReload,
                    ModConfig.maxStorageTypes, ModConfig.maxItemStackSize, ModConfig.baseMaxStorageTypes,
                    ModConfig.baseMaxItemStackSize, ModConfig.maxItemNbtSize, ModConfig.unconditionalWarehouse,
                    ModConfig.baseWarehouseActivationItem, ModConfig.fullWarehouseActivationItem, ModConfig.hopperRange,
                    ModConfig.hopperFrequency, ModConfig.lavaInfiniteThreshold, ModConfig.waterInfiniteThreshold,
                    ModConfig.riftUpgradeItem, ModConfig.riftChunkSize, ModConfig.riftPlotSpacingChunks,
                    ModConfig.riftFloorY, ModConfig.enableRiftForcedLoading, ModConfig.riftForcedLoadingRange,
                    ModConfig.enableRiftAvatar, ModConfig.enableRiftBorder, ModConfig.riftBorderWarningBlocks,
                    ModConfig.enableConduitUpgrade);

            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, sync);
            }
        });
    }

    public static void handleQueryConfigPermission(C2SQueryConfigPermissionPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            boolean canEdit = ModConfig.allowHotReload && context.server().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
            ServerPlayNetworking.send(player, new S2CConfigPermissionResultPayload(canEdit));
        });
    }

    public static void handleRefill(RefillPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled() || !warehouse.isCraftRefill()) return;

            net.minecraft.world.inventory.AbstractContainerMenu handler = player.containerMenu;
            if (!(handler instanceof net.minecraft.world.inventory.AbstractCraftingMenu) && !(handler instanceof CraftingWarehouseScreenHandler)) return;

            ItemStack template = payload.targetStack();
            if (template.isEmpty() || payload.slotIds().isEmpty()) return;

            java.util.List<Slot> targetSlots = new java.util.ArrayList<>();
            for (int requestedSlot : payload.slotIds()) {
                Slot slot = null;
                if (requestedSlot >= 0 && requestedSlot < handler.slots.size()) {
                    Slot potential = handler.getSlot(requestedSlot);
                    if (potential != null && potential.container instanceof net.minecraft.world.inventory.CraftingContainer && !(potential instanceof net.minecraft.world.inventory.ResultSlot)) {
                        slot = potential;
                    }
                }
                if (slot == null) {
                    for (Slot s : handler.slots) {
                        if (s != null && s.container instanceof net.minecraft.world.inventory.CraftingContainer && !(s instanceof net.minecraft.world.inventory.ResultSlot) && s.getContainerSlot() == requestedSlot) {
                            slot = s;
                            break;
                        }
                    }
                }

                if (slot == null || targetSlots.contains(slot)) continue;
                
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    if (slot.mayPlace(template)) targetSlots.add(slot);
                } else if (ItemStack.isSameItemSameComponents(stack, template)) {
                    targetSlots.add(slot);
                }
            }

            if (targetSlots.isEmpty()) return;

            int totalNeed = 0;
            int[] needs = new int[targetSlots.size()];

            for (int i = 0; i < targetSlots.size(); i++) {
                Slot slot = targetSlots.get(i);
                ItemStack stack = slot.getItem();
                int currentCount = stack.isEmpty() ? 0 : stack.getCount();
                int maxStackSize = Math.min(template.getMaxStackSize(), slot.getMaxStackSize());
                needs[i] = Math.max(0, maxStackSize - currentCount);
                totalNeed += needs[i];
            }

            if (totalNeed <= 0) return;

            final int requestedTotalNeed = totalNeed;
            java.util.Map<Slot, ItemStack> slotSnapshot = new LinkedHashMap<>();
            for (Slot s : targetSlots) slotSnapshot.put(s, s.getItem().copy());

            boolean changed;
            try {
                changed = WarehouseService.transaction(player, warehouse, "craft_refill", tx -> {
                    ItemStack taken = WarehouseManager.takeMatching(warehouse, template, requestedTotalNeed, true);
                    if (taken.isEmpty()) return false;

                    int remaining = taken.getCount();
                    int[] distribution = new int[targetSlots.size()];

                    while (remaining > 0) {
                        boolean anyAdded = false;
                        for (int i = 0; i < targetSlots.size() && remaining > 0; i++) {
                            if (distribution[i] < needs[i]) {
                                distribution[i]++;
                                remaining--;
                                anyAdded = true;
                            }
                        }
                        if (!anyAdded) break;
                    }

                    boolean appliedAny = false;
                    for (int i = 0; i < targetSlots.size(); i++) {
                        if (distribution[i] <= 0) continue;
                        Slot slot = targetSlots.get(i);
                        ItemStack stack = slot.getItem();
                        if (stack.isEmpty()) {
                            slot.set(taken.copyWithCount(distribution[i]));
                        } else {
                            stack.grow(distribution[i]);
                            slot.setChanged();
                        }
                        appliedAny = true;
                    }

                    if (!appliedAny) return false;
                    tx.commit();
                    handler.broadcastChanges();
                    return true;
                });
            } catch (RuntimeException | Error throwable) {
                for (Map.Entry<Slot, ItemStack> entry : slotSnapshot.entrySet()) entry.getKey().set(entry.getValue().copy());
                throw throwable;
            }
            if (!changed) {
                for (Map.Entry<Slot, ItemStack> entry : slotSnapshot.entrySet()) entry.getKey().set(entry.getValue().copy());
            }
        });
    }

    public static void handleUpdateHopperFilters(C2SUpdateHopperFiltersPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            WarehouseService.commitIfWarehouseChanged(player, getWarehouse(player), "update_hopper_filters", () -> {
                getWarehouse(player).setHopperFilters(payload.filters(), payload.blacklist());
                return null;
            });
        });
    }

    public static void handleUpdateFoodFilters(C2SUpdateFoodFiltersPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            WarehouseService.commitIfWarehouseChanged(player, getWarehouse(player), "update_food_filters", () -> {
                getWarehouse(player).setFoodFilters(payload.filters(), payload.blacklist());
                return null;
            });
        });
    }

    public static void handleUpdateForbiddenPlayers(C2SUpdateForbiddenPlayersPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            WarehouseService.commitIfWarehouseChanged(player, getWarehouse(player), "update_forbidden_players", () -> {
                getWarehouse(player).setForbidden(payload.playerUuid(), payload.forbidden());
                return null;
            });
        });
    }

    public static void handleUpdateFrozenState(C2SUpdateFrozenStatePayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            PlayerWarehouse warehouse = getWarehouse(context.player());
            if (warehouse.isFrozen() == payload.frozen()) return;
            warehouse.setFrozen(payload.frozen());
            syncChanges(context.player());
        });
    }

    public static void handleDoubleClickQuickStore(C2SDoubleClickQuickStorePayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);

            if (!warehouse.isEnabled() || !warehouse.isQuickInteraction() || warehouse.isFolded()) return;

            ItemStack cursorStack = player.containerMenu.getCarried();
            if (cursorStack.isEmpty()) return;

            boolean storedAny = WarehouseService.commitIfWarehouseChanged(player, warehouse, "double_click_quick_store", () -> {
                boolean changed = false;
                for (int i = 0; i < player.containerMenu.slots.size(); i++) {
                    net.minecraft.world.inventory.Slot slot = player.containerMenu.slots.get(i);
                    if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                        int containerSlot = slot.getContainerSlot();
                        if (containerSlot >= 0 && containerSlot < 36) {
                            ItemStack stack = slot.getItem();
                            if (!stack.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(cursorStack, stack)) {
                                int beforeCount = stack.getCount();
                                ItemStack remaining = WarehouseManager.addFluid(warehouse, stack, player);
                                slot.set(remaining);
                                if (remaining.getCount() < beforeCount) changed = true;
                            }
                        }
                    }
                }
                return changed;
            });

            if (storedAny && player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
        });
    }

    public static void handleTogglePinned(C2STogglePinnedPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            WarehouseService.commitIfWarehouseChanged(player, warehouse, "toggle_pinned", () -> {
                warehouse.togglePinned(payload.slotId());
                return null;
            });
            int sortedIndex = payload.slotId() + warehouse.getScrollOffset() * 9;
            var sorted = warehouse.getSortedEntries();
            if (sortedIndex >= 0 && sortedIndex < sorted.size()) {
                boolean pinned = sorted.get(sortedIndex).isPinned();
                ServerPlayNetworking.send(player, new S2CWarehousePinnedUpdatePayload(sortedIndex, pinned));
            }
        });
    }

    public static void handleRecipeTransfer(C2SRecipeTransferPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;

            boolean isWarehouseCrafting = menu instanceof CraftingWarehouseScreenHandler;
            boolean isVanillaInventory = menu instanceof net.minecraft.world.inventory.InventoryMenu;

            if (!isWarehouseCrafting && !isVanillaInventory) return;

            String recipeIdRaw = payload.recipeId();
            String recipeIdStr = recipeIdRaw.contains(" / ") ? recipeIdRaw.substring(recipeIdRaw.indexOf(" / ") + 3) : recipeIdRaw;
            if (recipeIdStr.endsWith("]")) recipeIdStr = recipeIdStr.substring(0, recipeIdStr.length() - 1);
            
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(recipeIdStr);
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id);
            net.minecraft.world.item.crafting.RecipeHolder<?> recipeHolder = ((ServerLevel) player.level()).getServer().getRecipeManager().byKey(key).orElse(null);
            if (recipeHolder == null || !(recipeHolder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) return;

            PlayerWarehouse warehouse = getWarehouse(player);
            if (!warehouse.isEnabled()) return;

            boolean is3x3 = isWarehouseCrafting || (isVanillaInventory && com.portablestorage.util.WarehouseUtils.is3x3Enabled(player));
            int gridWidth = is3x3 ? 3 : 2;
            int gridHeight = is3x3 ? 3 : 2;

            java.util.List<Slot> craftingSlots = new ArrayList<>();
            for (Slot slot : menu.slots) {
                if (slot != null && slot.container instanceof net.minecraft.world.inventory.CraftingContainer && !(slot instanceof net.minecraft.world.inventory.ResultSlot)) {
                    craftingSlots.add(slot);
                }
            }

            java.util.Map<Slot, ItemStack> slotSnapshot = new LinkedHashMap<>();
            for (Slot s : craftingSlots) slotSnapshot.put(s, s.getItem().copy());

            List<ItemStack> inventory = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                inventory.add(player.getInventory().getItem(i).copy());
            }

            boolean completed;
            try {
                completed = WarehouseService.transaction(player, warehouse, "recipe_transfer", tx -> {
                    if (craftingSlots.isEmpty()) return false;

                    for (Slot slot : craftingSlots) {
                        ItemStack stack = slot.getItem();
                        if (stack.isEmpty()) continue;
                        ItemStack remaining = stack.copy();
                        WarehouseManager.addItem(warehouse, remaining, player);
                        if (!remaining.isEmpty()) return false;
                        slot.set(ItemStack.EMPTY);
                    }

                    var placement = recipe.placementInfo();
                    java.util.List<net.minecraft.world.item.crafting.Ingredient> ingredients = placement.ingredients();
                    int gridSize = gridWidth * gridHeight;

                    int recipeWidth = gridWidth;
                    int recipeHeight = gridHeight;
                    if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                        recipeWidth = shaped.getWidth();
                        recipeHeight = shaped.getHeight();
                    } else {
                        recipeWidth = (ingredients.size() <= 4 && !is3x3) ? 2 : 3;
                        recipeHeight = (int) Math.ceil((double) ingredients.size() / recipeWidth);
                    }
                    if (recipeWidth > gridWidth || recipeHeight > gridHeight) return false;

                    it.unimi.dsi.fastutil.ints.IntList slotToIngredientIndex = placement.slotsToIngredientIndex();
                    Map<Integer, ItemStack> plannedStacks = new LinkedHashMap<>();

                    for (int targetContainerSlot = 0; targetContainerSlot < slotToIngredientIndex.size() && targetContainerSlot < gridSize; targetContainerSlot++) {
                        int ingredientIdx = slotToIngredientIndex.getInt(targetContainerSlot);
                        if (ingredientIdx == net.minecraft.world.item.crafting.PlacementInfo.EMPTY_SLOT || ingredientIdx < 0 || ingredientIdx >= ingredients.size()) continue;

                        net.minecraft.world.item.crafting.Ingredient ingredient = ingredients.get(ingredientIdx);
                        if (ingredient == null || ingredient.isEmpty()) continue;

                        ItemStack found = WarehouseManager.takeMatchingIngredient(warehouse, ingredient, 1);
                        if (found.isEmpty()) {
                            int inventorySize = Math.min(36, player.getInventory().getContainerSize());
                            for (int invIdx = 0; invIdx < inventorySize; invIdx++) {
                                ItemStack invStack = player.getInventory().getItem(invIdx);
                                if (ingredient.test(invStack)) {
                                    found = player.getInventory().removeItem(invIdx, 1);
                                    break;
                                }
                            }
                        }

                        if (found.isEmpty()) return false;
                        plannedStacks.put(targetContainerSlot, found);
                    }

                    for (Map.Entry<Integer, ItemStack> entry : plannedStacks.entrySet()) {
                        Slot targetSlot = null;
                        for (Slot slot : menu.slots) {
                            if (slot != null && slot.container instanceof net.minecraft.world.inventory.CraftingContainer && !(slot instanceof net.minecraft.world.inventory.ResultSlot) && slot.getContainerSlot() == entry.getKey()) {
                                targetSlot = slot;
                                break;
                            }
                        }
                        if (targetSlot == null || !targetSlot.mayPlace(entry.getValue())) return false;
                        targetSlot.set(entry.getValue());
                    }

                    tx.commit();
                    menu.broadcastChanges();
                    return true;
                });
            } catch (RuntimeException | Error throwable) {
                for (Map.Entry<Slot, ItemStack> entry : slotSnapshot.entrySet()) entry.getKey().set(entry.getValue().copy());
                int inventorySize = Math.min(inventory.size(), player.getInventory().getContainerSize());
                for (int i = 0; i < inventorySize; i++) player.getInventory().setItem(i, inventory.get(i).copy());
                player.getInventory().setChanged();
                throw throwable;
            }
            if (!completed) {
                for (Map.Entry<Slot, ItemStack> entry : slotSnapshot.entrySet()) entry.getKey().set(entry.getValue().copy());
                int inventorySize = Math.min(inventory.size(), player.getInventory().getContainerSize());
                for (int i = 0; i < inventorySize; i++) player.getInventory().setItem(i, inventory.get(i).copy());
                player.getInventory().setChanged();
            }
        });
    }

    public static void handleRequestWarehouseSnapshot(C2SRequestWarehouseSnapshotPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> WarehouseService.sync(context.player()));
    }

    public static void handleQuickToolSwap(C2SQuickToolSwapPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ServerPlayer player = context.player();
            PlayerWarehouse warehouse = getWarehouse(player);
            int slot = payload.slot();
            if (!warehouse.isEnabled() || warehouse.getUpgrade(com.portablestorage.upgrade.ToolUpgrade.ID).isEmpty()) return;
            if (slot < 0 || slot >= com.portablestorage.storage.key.ToolWarehouseKey.SLOT_COUNT) return;

            ItemStack toolStack = warehouse.getToolSlotStack(slot);
            ItemStack handStack = player.getMainHandItem();
            
            boolean handIsTool = !handStack.isEmpty() && (handStack.has(net.minecraft.core.component.DataComponents.TOOL) || handStack.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE));

            if (toolStack.isEmpty()) {
                if (handStack.isEmpty() || !handIsTool) return;
                
                int typeLimit = warehouse.getMaxStorageTypes();
                if (typeLimit >= 0 && warehouse.getStoredItemTypeCount() >= typeLimit) return;
                if (!WarehouseManager.canStoreItem(warehouse, handStack, player, "quick_tool_swap.hand")) return;

                ItemStack handSnapshot = handStack.copy();
                boolean completed;
                try {
                    completed = WarehouseService.transaction(player, warehouse, "quick_tool_swap.store_hand", tx -> {
                        warehouse.setToolSlotStack(slot, handSnapshot.copyWithCount(1));
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        tx.commit();
                        if (player.containerMenu != null) player.containerMenu.broadcastChanges();
                        return true;
                    });
                } catch (RuntimeException | Error throwable) {
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
                    throw throwable;
                }
                if (!completed) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
                return;
            }

            if (!handStack.isEmpty() && !handIsTool) return;
            
            if (!handStack.isEmpty()) {
                if (!WarehouseManager.canStoreItem(warehouse, handStack, player, "quick_tool_swap.hand")) return;
            }

            ItemStack handSnapshot = handStack.copy();
            ItemStack toolSnapshot = toolStack.copy();
            boolean completed;
            try {
                completed = WarehouseService.transaction(player, warehouse, "quick_tool_swap.exchange", tx -> {
                    if (handSnapshot.isEmpty()) {
                        warehouse.setToolSlotStack(slot, ItemStack.EMPTY);
                    } else {
                        warehouse.setToolSlotStack(slot, handSnapshot.copyWithCount(1));
                    }
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, toolSnapshot.copy());
                    tx.commit();
                    if (player.containerMenu != null) player.containerMenu.broadcastChanges();
                    return true;
                });
            } catch (RuntimeException | Error throwable) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
                throw throwable;
            }
            if (!completed) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handSnapshot);
        });
    }

    private static PlayerWarehouse getWarehouse(ServerPlayer player) {
        return WarehouseService.get(player);
    }

    private static void syncChanges(ServerPlayer player) {
        if (player.containerMenu != null) player.containerMenu.broadcastChanges();
        WarehouseService.commit(player, "server_network.change");
    }
}