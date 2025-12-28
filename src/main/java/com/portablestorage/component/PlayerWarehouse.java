package com.portablestorage.component;

import com.portablestorage.mixin.accessor.AbstractContainerMenuAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public class PlayerWarehouse implements Container {
    private final List<WarehouseEntry> storage = new ArrayList<>();
    private int scrollOffset = 0;
    private int visibleRows = 6;
    private String searchText = "";
    private boolean isFolded = true;
    private int sortMode = 0; // 0: 数量, 1: 名称, 2: ID, 3: 更新时间
    private boolean isAscending = false;
    private boolean quickInteraction = true;
    private boolean smartCollapse = false;
    private boolean craftRefill = true;
    private boolean enabled = true;
    private List<WarehouseEntry> sortedCache = null;
    private final Consumer<PlayerWarehouse> onChanged;

    public PlayerWarehouse(UUID id, Consumer<PlayerWarehouse> onChanged) {
        this.onChanged = onChanged;
    }

    private net.minecraft.world.item.Item getVirtualFluidForItem(net.minecraft.world.item.Item item) {
        if (item == net.minecraft.world.item.Items.LAVA_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_LAVA;
        if (item == net.minecraft.world.item.Items.WATER_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_WATER;
        if (item == net.minecraft.world.item.Items.MILK_BUCKET) return com.portablestorage.item.ModItems.VIRTUAL_MILK;
        return null;
    }

    private boolean isVirtualFluid(net.minecraft.world.item.Item item) {
        return item == com.portablestorage.item.ModItems.VIRTUAL_LAVA || 
               item == com.portablestorage.item.ModItems.VIRTUAL_WATER || 
               item == com.portablestorage.item.ModItems.VIRTUAL_MILK;
    }

    private int findEmptyBucket(Player player) {
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            Slot slot = player.containerMenu.slots.get(i);
            if (slot.container instanceof Inventory && slot.getItem().is(net.minecraft.world.item.Items.BUCKET)) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack getFluidBucket(net.minecraft.world.item.Item virtualFluid) {
        if (virtualFluid == com.portablestorage.item.ModItems.VIRTUAL_LAVA) return new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);
        if (virtualFluid == com.portablestorage.item.ModItems.VIRTUAL_WATER) return new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
        if (virtualFluid == com.portablestorage.item.ModItems.VIRTUAL_MILK) return new ItemStack(net.minecraft.world.item.Items.MILK_BUCKET);
        return ItemStack.EMPTY;
    }

    public void setSearchText(String text) {
        this.searchText = text.toLowerCase();
        this.scrollOffset = 0;
        this.setChanged();
    }

    /**
     * 尝试存入流体并转换桶。
     * @param stack 流体桶堆叠
     * @return 转换后应该留在原处（或光标上）的物品（可能是剩余流体桶，或者是空桶，或者是两者的混合 - 混合情况将优先返回剩余流体桶并将空桶尝试放入背包）
     */
    public ItemStack addFluid(ItemStack stack, Player player) {
        if (!enabled || !quickInteraction) {
            addItemInternal(stack);
            return stack;
        }

        net.minecraft.world.item.Item virtualItem = getVirtualFluidForItem(stack.getItem());
        if (virtualItem == null) {
            addItemInternal(stack);
            return stack;
        }

        int originalCount = stack.getCount();
        ItemStack virtualStack = new ItemStack(virtualItem, originalCount);
        addItemInternal(virtualStack);
        
        int stored = originalCount - virtualStack.getCount();
        if (stored > 0) {
            ItemStack emptyBuckets = new ItemStack(net.minecraft.world.item.Items.BUCKET, stored);
            stack.shrink(stored); // 减少原本的流体桶
            
            if (stack.isEmpty()) {
                // 全部转换为了流体
                return emptyBuckets;
            } else {
                // 部分转换，剩余部分仍然是流体桶。空桶需要额外处理（放入背包）。
                if (!player.getInventory().add(emptyBuckets)) {
                    player.drop(emptyBuckets, false);
                }
                return stack;
            }
        }
        
        return stack;
    }

    public void addItem(ItemStack stack) {
        addItemInternal(stack);
    }

    private void addItemInternal(ItemStack stack) {
        if (stack.isEmpty()) return;
        long limit = com.portablestorage.config.ModConfig.maxItemStackSize;
        boolean changed = false;

        // 1. 查找是否已有相同条目
        WarehouseEntry existingEntry = null;
        for (WarehouseEntry entry : storage) {
            if (entry.matches(stack)) {
                existingEntry = entry;
                break;
            }
        }

        if (existingEntry != null) {
            // 已有该种类：仅尝试合并
            if (limit > 0) {
                long current = existingEntry.getCount();
                long canAdd = Math.max(0, limit - current);
                if (canAdd > 0) {
                    int toAdd = (int) Math.min(stack.getCount(), canAdd);
                    existingEntry.add(toAdd);
                    stack.shrink(toAdd);
                    changed = true;
                }
            } else {
                existingEntry.add(stack.getCount());
                stack.setCount(0);
                changed = true;
            }
        } else {
            // 没有该种类：检查种类上限并创建新条目
            int typeLimit = com.portablestorage.config.ModConfig.maxStorageTypes;
            if (typeLimit < 0 || storage.size() < typeLimit) {
                if (limit > 0) {
                    int toAdd = (int) Math.min(stack.getCount(), (int) Math.min(stack.getCount(), limit));
                    if (toAdd > 0) {
                        storage.add(new WarehouseEntry(stack.copyWithCount(toAdd), toAdd));
                        stack.shrink(toAdd);
                        changed = true;
            }
                } else {
        storage.add(new WarehouseEntry(stack.copy(), stack.getCount()));
        stack.setCount(0);
                    changed = true;
                }
            }
        }

        if (changed) {
        this.setChanged();
        }
    }

    public ItemStack removeItem(int slot, int amount) {
        return removeItem(slot, amount, false);
    }

    public ItemStack removeItem(int slot, int amount, boolean force) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slot + (scrollOffset * 9);
        if (actualIndex >= 0 && actualIndex < sorted.size()) {
            WarehouseEntry entry = sorted.get(actualIndex);
            
            // 1. 拦截智能折叠的展示项
            net.minecraft.world.item.component.CustomData customData = entry.getItemStack().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            boolean isCollapsed = customData != null && customData.copyTag().getBoolean(com.portablestorage.util.WarehouseConstants.SMART_COLLAPSE_TAG);
            if (!force && smartCollapse && searchText.isEmpty() && isCollapsed) {
                return ItemStack.EMPTY;
            }

            // 2. 拦截虚拟流体项（除非强制，例如流体桶提取逻辑）
            if (!force && isVirtualFluid(entry.getItemStack().getItem())) {
                return ItemStack.EMPTY;
            }

            long toRemove = Math.min(amount, entry.getCount());
            ItemStack result = entry.getItemStack().copyWithCount((int)toRemove);
            entry.subtract(toRemove);
            if (entry.getCount() <= 0) storage.remove(entry);
            this.setChanged();
            return result;
        }
        return ItemStack.EMPTY;
    }

    public List<WarehouseEntry> getSortedEntries() {
        if (sortedCache == null) {
            List<WarehouseEntry> filtered = storage;
            if (!searchText.isEmpty()) {
                String query = searchText.toLowerCase().trim();
                boolean exact = query.startsWith("!") && query.endsWith("!") && query.length() > 2;
                final String finalQuery = exact ? query.substring(1, query.length() - 1) : query;

                filtered = storage.stream()
                        .filter(entry -> {
                            ItemStack stack = entry.getItemStack();
                            // 1. 名称搜索
                            String name = stack.getHoverName().getString().toLowerCase();
                            if (exact ? name.equals(finalQuery) : name.contains(finalQuery)) return true;

                            // 2. ID 搜索
                            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
                            if (exact ? id.equals(finalQuery) : id.contains(finalQuery)) return true;

                            // 3. 描述 (Lore) 搜索
                            net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
                            if (lore != null) {
                                for (net.minecraft.network.chat.Component line : lore.lines()) {
                                    String lineText = line.getString().toLowerCase();
                                    if (exact ? lineText.equals(finalQuery) : lineText.contains(finalQuery)) return true;
                                }
                            }
                            return false;
                        })
                        .toList();
            }

            if (smartCollapse && searchText.isEmpty()) {
                // 按物品类型分组折叠（不同 NBT 但相同 Item）
                Map<net.minecraft.world.item.Item, List<WarehouseEntry>> groups = new LinkedHashMap<>();
                for (WarehouseEntry entry : filtered) {
                    groups.computeIfAbsent(entry.getItemStack().getItem(), k -> new ArrayList<>()).add(entry);
                }

                List<WarehouseEntry> collapsed = new ArrayList<>();
                for (Map.Entry<net.minecraft.world.item.Item, List<WarehouseEntry>> group : groups.entrySet()) {
                    List<WarehouseEntry> entries = group.getValue();
                    if (entries.size() > 1) {
                        long totalCount = 0;
                        long lastUpdated = 0;
                        for (WarehouseEntry e : entries) {
                            totalCount += e.getCount();
                            lastUpdated = Math.max(lastUpdated, e.getLastUpdated());
                        }
                        
                        // 创建一个无 NBT 的展示 ItemStack，并添加光效
                        ItemStack displayStack = new ItemStack(group.getKey());
                        displayStack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                        
                        // 使用自定义组件标记这是合并条目
                        displayStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, 
                            net.minecraft.world.item.component.CustomData.of(new net.minecraft.nbt.CompoundTag() {{
                                putBoolean(com.portablestorage.util.WarehouseConstants.SMART_COLLAPSE_TAG, true);
                            }}));
                        
                        WarehouseEntry merged = new WarehouseEntry(displayStack, totalCount);
                        collapsed.add(merged);
                    } else {
                        collapsed.add(entries.get(0));
                    }
                }
                sortedCache = new ArrayList<>(collapsed);
            } else {
            sortedCache = new ArrayList<>(filtered);
            }
            
            Comparator<WarehouseEntry> comparator = switch (sortMode) {
                case 0 -> Comparator.comparingLong(WarehouseEntry::getCount);
                case 1 -> Comparator.comparing(e -> e.getItemStack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
                case 2 -> Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
                case 3 -> Comparator.comparingLong(WarehouseEntry::getLastUpdated);
                default -> (a, b) -> 0;
            };

            if (!isAscending) comparator = comparator.reversed();
            
            // 稳定性排序：如果相等，按 ID 排序
            comparator = comparator.thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()));
            
            sortedCache.sort(comparator);
        }
        return sortedCache;
    }

    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int offset) {
        int maxRows = (int) Math.ceil(getSortedEntries().size() / 9.0);
        int maxOffset = Math.max(0, maxRows - visibleRows);
        this.scrollOffset = Math.clamp(offset, 0, maxOffset);
        this.setChanged();
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public void setVisibleRows(int rows) {
        this.visibleRows = Math.clamp(rows, 1, 12);
        this.scrollOffset = 0;
        this.setChanged();
    }

    public boolean isFolded() { return isFolded; }
    public void setFolded(boolean folded) { 
        if (!enabled && !folded) return; // 禁用时不允许展开
        this.isFolded = folded; 
        this.setChanged(); 
    }

    public int getSortMode() { return sortMode; }
    public void setSortMode(int mode) { this.sortMode = mode; this.setChanged(); }

    public boolean isAscending() { return isAscending; }
    public void setAscending(boolean ascending) { this.isAscending = ascending; this.setChanged(); }

    public boolean isQuickInteraction() { return quickInteraction; }
    public void setQuickInteraction(boolean quick) { this.quickInteraction = quick; this.setChanged(); }

    public boolean isSmartCollapse() { return smartCollapse; }
    public void setSmartCollapse(boolean smart) { this.smartCollapse = smart; this.setChanged(); }

    public boolean isCraftRefill() { return craftRefill; }
    public void setCraftRefill(boolean refill) { this.craftRefill = refill; this.setChanged(); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; this.setChanged(); }

    public String getSearchText() { return searchText; }

    /**
     * 服务器端执行：将仓库中的物品快速转移到玩家背包
     */
    public void tryTransferToInventory(int slotIndex, Player player) {
        if (!enabled || !quickInteraction) return;

        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        if (actualIndex < 0 || actualIndex >= sorted.size()) return;

        WarehouseEntry entry = sorted.get(actualIndex);
        ItemStack stackInSlot = entry.getItemStack();
        if (stackInSlot.isEmpty()) return;

        // 1. 特殊逻辑：流体提取
        if (isVirtualFluid(stackInSlot.getItem())) {
            // 查找玩家背包中是否有空桶
            int emptyBucketSlot = findEmptyBucket(player);
            if (emptyBucketSlot != -1) {
                ItemStack fluidBucket = getFluidBucket(stackInSlot.getItem());
                if (!fluidBucket.isEmpty()) {
                    Slot slot = player.containerMenu.getSlot(emptyBucketSlot);
                    ItemStack bucketStack = slot.getItem();
                    
                    // 转换一个空桶
                    bucketStack.shrink(1);
                    if (bucketStack.isEmpty()) slot.set(ItemStack.EMPTY);
                    
                    // 尝试放入流体桶
                    if (!player.getInventory().add(fluidBucket)) {
                        player.drop(fluidBucket, false);
                    }
                    
                    // 消耗仓库流体
                    this.removeItem(slotIndex, 1, true);
                    player.containerMenu.broadcastChanges();
                }
            }
            return;
        }

        long realCount = entry.getCount();
        int toTake = (int) Math.min(stackInSlot.getMaxStackSize(), realCount);
        ItemStack resultStack = stackInSlot.copyWithCount(toTake);

        // 查找玩家背包在当前菜单中的索引范围
        int inventoryStart = -1;
        int inventoryEnd = -1;
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            Slot slot = player.containerMenu.slots.get(i);
            if (slot.container instanceof Inventory) {
                if (inventoryStart == -1) inventoryStart = i;
                inventoryEnd = i + 1;
            }
        }

        if (inventoryStart != -1 && ((AbstractContainerMenuAccessor) player.containerMenu).invokeMoveItemStackTo(resultStack, 
                inventoryStart, inventoryEnd, true)) {
            int movedCount = toTake - resultStack.getCount();
            if (movedCount > 0) {
                this.removeItem(slotIndex, movedCount);
            }
        }
    }

    /**
     * 服务器端执行：从仓库中取出指定数量的匹配物品
     * @param template 模板物品
     * @param amount 数量
     * @param matchComponents 是否需要完全匹配组件（NBT）
     */
    public ItemStack takeMatching(ItemStack template, int amount, boolean matchComponents) {
        int totalTaken = 0;
        ItemStack result = template.copyWithCount(0);
        
        for (int i = storage.size() - 1; i >= 0; i--) {
            WarehouseEntry entry = storage.get(i);
            boolean matches = matchComponents ? entry.matches(template) : entry.getItemStack().is(template.getItem());
            
            if (matches) {
                long canTake = Math.min(amount - totalTaken, entry.getCount());
                if (canTake > 0) {
                    entry.subtract(canTake);
                    totalTaken += (int)canTake;
                    if (entry.getCount() <= 0) storage.remove(i);
                }
            }
            if (totalTaken >= amount) break;
        }
        
        if (totalTaken > 0) {
            result.setCount(totalTaken);
            this.setChanged();
        }
        return result;
    }

    public ItemStack takeMatching(ItemStack template, int amount) {
        return takeMatching(template, amount, true);
    }

    public long getRealCount(int slotIndex) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slotIndex + (scrollOffset * 9);
        return (actualIndex >= 0 && actualIndex < sorted.size()) ? sorted.get(actualIndex).getCount() : 0;
    }

    public void readNbt(CompoundTag tag, HolderLookup.Provider registries) {
        storage.clear();
        ListTag list = tag.getList("storage", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            storage.add(WarehouseEntry.fromNbt(list.getCompound(i), registries));
        }
        this.scrollOffset = tag.getInt("scrollOffset");
        this.visibleRows = tag.contains("visibleRows") ? tag.getInt("visibleRows") : 6;
        this.isFolded = tag.contains("isFolded") ? tag.getBoolean("isFolded") : true;
        this.sortMode = tag.getInt("sortMode");
        this.isAscending = tag.getBoolean("isAscending");
        this.quickInteraction = tag.contains("quickInteraction") ? tag.getBoolean("quickInteraction") : true;
        this.smartCollapse = tag.getBoolean("smartCollapse");
        this.craftRefill = !tag.contains("craftRefill") || tag.getBoolean("craftRefill");
        this.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        this.searchText = tag.getString("searchText");
        this.sortedCache = null;
    }

    public void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (WarehouseEntry entry : storage) list.add(entry.toNbt(registries));
        tag.put("storage", list);
        tag.putInt("scrollOffset", scrollOffset);
        tag.putInt("visibleRows", visibleRows);
        tag.putBoolean("isFolded", isFolded);
        tag.putInt("sortMode", sortMode);
        tag.putBoolean("isAscending", isAscending);
        tag.putBoolean("quickInteraction", quickInteraction);
        tag.putBoolean("smartCollapse", smartCollapse);
        tag.putBoolean("craftRefill", craftRefill);
        tag.putBoolean("enabled", enabled);
        tag.putString("searchText", searchText);
    }

    @Override public int getContainerSize() { return visibleRows * 9; }
    @Override public boolean isEmpty() { return storage.isEmpty(); }
    @Override public ItemStack getItem(int slot) {
        List<WarehouseEntry> sorted = getSortedEntries();
        int actualIndex = slot + (scrollOffset * 9);
        return (actualIndex >= 0 && actualIndex < sorted.size()) ? sorted.get(actualIndex).getItemStack().copyWithCount(1) : ItemStack.EMPTY;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
    
    // 仓库内容由 CCA 同步和我们的自定义点击逻辑管理，
    // 忽略原版容器的 setItem 同步调用，防止数量在客户端意外叠加。
    @Override public void setItem(int slot, ItemStack stack) { }
    
    @Override public void setChanged() { this.sortedCache = null; if (onChanged != null) onChanged.accept(this); }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() { storage.clear(); this.setChanged(); }
}

