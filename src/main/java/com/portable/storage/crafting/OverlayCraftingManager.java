package com.portable.storage.crafting;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个玩家一个虚拟 3x3 合成网格（槽 1..9）与结果槽（槽 0）。
 * 槽位内容在关闭界面后仍保留（驻留内存；玩家下线后不持久化）。
 */
public final class OverlayCraftingManager {
    private OverlayCraftingManager() {}

    public static final class State {
        public final ItemStack[] slots = new ItemStack[10]; // 0 = result, 1..9 = inputs
        public State() {
            Arrays.fill(slots, ItemStack.EMPTY);
        }
    }

    private static final Map<java.util.UUID, State> STATE_BY_PLAYER = new ConcurrentHashMap<>();

    public static State get(ServerPlayerEntity player) {
        return STATE_BY_PLAYER.computeIfAbsent(player.getUuid(), k -> new State());
    }

    public static void clear(ServerPlayerEntity player) {
        STATE_BY_PLAYER.remove(player.getUuid());
    }

    /**
     * 将虚拟合成槽内的所有输入物品返还（优先放入背包，其次丢弃在地上）。
     */
    public static void refundAll(ServerPlayerEntity player) {
        State st = get(player);
        for (int i = 1; i <= 9; i++) {
            ItemStack s = st.slots[i];
            if (s.isEmpty()) continue;
            ItemStack copy = s.copy();
            st.slots[i] = ItemStack.EMPTY;
            if (!player.getInventory().insertStack(copy)) {
                player.dropItem(copy, false);
            }
        }
        // 结果槽不返还（它是计算产物），仅清空显示
        st.slots[0] = ItemStack.EMPTY;
        updateResult(player, st);
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void setInput(ServerPlayerEntity player, int slot1to9, ItemStack stack) {
        if (slot1to9 < 1 || slot1to9 > 9) return;
        State st = get(player);
        st.slots[slot1to9] = stack.copy();
        updateResult(player, st);
    }

    public static void updateResult(ServerPlayerEntity player) {
        updateResult(player, get(player));
    }

    private static void updateResult(ServerPlayerEntity player, State st) {
        World world = player.getWorld();
        java.util.ArrayList<ItemStack> list = new java.util.ArrayList<>(9);
        for (int i = 1; i <= 9; i++) list.add(st.slots[i].copy());
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, list);
        RecipeEntry<?> match = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).orElse(null);
        if (match != null && match.value() instanceof net.minecraft.recipe.CraftingRecipe recipe) {
            ItemStack out = recipe.craft(input, world.getRegistryManager());
            st.slots[0] = out;
        } else {
            st.slots[0] = ItemStack.EMPTY;
        }
    }

    /**
     * 原版点击语义（简化）：
     * - 输入槽 1..9：左键交换/合并，右键放/取一个，使用服务端 cursorStack。
     * - 结果槽 0：左键取一个结果并消耗配方材料；Shift 左键尽可能多地合成并放入背包。
     */
    public static void handleClick(ServerPlayerEntity player, int slotIndex, int button, boolean shift) {
        // 特殊：slotIndex == -1 表示客户端请求“关闭界面返还所有输入”
        if (slotIndex == -1) {
            refundAll(player);
            return;
        }
        State st = get(player);
        if (slotIndex == 0) {
            handleResultClick(player, st, button, shift);
            return;
        }
        if (slotIndex >= 1 && slotIndex <= 9) {
            handleInputClick(player, st, slotIndex, button, shift);
            return;
        }
    }

    private static void handleInputClick(ServerPlayerEntity player, State st, int slot, int button, boolean shift) {
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        ItemStack slotStack = st.slots[slot];
        
        if (shift) {
            // Shift+点击：移动到背包
            if (!slotStack.isEmpty()) {
                if (!player.getInventory().insertStack(slotStack)) {
                    // 放不下则丢在地上
                    player.dropItem(slotStack, false);
                }
                st.slots[slot] = ItemStack.EMPTY;
            }
            updateResult(player);
            return;
        }
        
        if (button == 0) { // 左键
            if (cursor.isEmpty()) {
                // 拿起槽内
                player.currentScreenHandler.setCursorStack(slotStack.copy());
                st.slots[slot] = ItemStack.EMPTY;
            } else if (slotStack.isEmpty()) {
                // 放下全部
                st.slots[slot] = cursor.copy();
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            } else if (ItemStack.areItemsAndComponentsEqual(cursor, slotStack)) {
                // 合并
                int max = Math.min(slotStack.getMaxCount(), player.getInventory().getMaxCountPerStack());
                int can = Math.min(cursor.getCount(), Math.max(0, max - slotStack.getCount()));
                if (can > 0) {
                    slotStack.increment(can);
                    cursor.decrement(can);
                    player.currentScreenHandler.setCursorStack(cursor);
                } else {
                    // 交换
                    st.slots[slot] = cursor.copy();
                    player.currentScreenHandler.setCursorStack(slotStack.copy());
                }
            } else {
                // 交换
                st.slots[slot] = cursor.copy();
                player.currentScreenHandler.setCursorStack(slotStack.copy());
            }
        } else if (button == 1) { // 右键
            if (cursor.isEmpty()) {
                if (!slotStack.isEmpty()) {
                    int half = (int)Math.ceil(slotStack.getCount() / 2.0);
                    ItemStack taken = slotStack.copy();
                    taken.setCount(half);
                    slotStack.decrement(half);
                    if (slotStack.isEmpty()) st.slots[slot] = ItemStack.EMPTY;
                    player.currentScreenHandler.setCursorStack(taken);
                }
            } else {
                if (slotStack.isEmpty()) {
                    ItemStack put = cursor.copy();
                    put.setCount(1);
                    st.slots[slot] = put;
                    cursor.decrement(1);
                    player.currentScreenHandler.setCursorStack(cursor);
                } else if (ItemStack.areItemsAndComponentsEqual(cursor, slotStack)) {
                    int max = Math.min(slotStack.getMaxCount(), player.getInventory().getMaxCountPerStack());
                    if (slotStack.getCount() < max && cursor.getCount() > 0) {
                        slotStack.increment(1);
                        cursor.decrement(1);
                        player.currentScreenHandler.setCursorStack(cursor);
                    }
                }
            }
        }
        updateResult(player, st);
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void handleResultClick(ServerPlayerEntity player, State st, int button, boolean shift) {
        if (st.slots[0].isEmpty()) return;
        // 复制一份结果模板
        ItemStack resultTemplate = st.slots[0].copy();
        World world = player.getWorld();

        // 计算最多可合成次数（按材料限制）
        int maxCrafts = maxCraftableTimes(world, st);
        if (maxCrafts <= 0) return;

        if (shift) {
            // 尽可能多，直接塞进玩家背包
            int crafted = 0;
            while (crafted < maxCrafts) {
                ItemStack out = resultTemplate.copy();
                if (!player.getInventory().insertStack(out)) break;
                consumeOnce(player, st);
                crafted++;
            }
        } else {
            // 单次：放到光标（若光标为空或可合堆）否则塞背包
            ItemStack out = resultTemplate.copy();
            ItemStack cursor = player.currentScreenHandler.getCursorStack();
            // 右键：只取 1 个
            if (button == 1) out.setCount(1);
            boolean placed = tryPlaceToCursorOrInventory(player, cursor, out);
            consumeOnce(player, st);
        }

        updateResult(player, st);
        player.currentScreenHandler.sendContentUpdates();
    }

    private static boolean tryPlaceToCursorOrInventory(ServerPlayerEntity player, ItemStack cursor, ItemStack out) {
        boolean placed = false;
        if (cursor.isEmpty()) {
            player.currentScreenHandler.setCursorStack(out);
            placed = true;
        } else if (ItemStack.areItemsAndComponentsEqual(cursor, out)) {
            int max = Math.min(out.getMaxCount(), player.getInventory().getMaxCountPerStack());
            int can = Math.min(out.getCount(), Math.max(0, max - cursor.getCount()));
            if (can > 0) {
                cursor.increment(can);
                out.decrement(can);
                player.currentScreenHandler.setCursorStack(cursor);
                placed = out.isEmpty();
            }
        }
        if (!placed) {
            if (!player.getInventory().insertStack(out)) {
                player.dropItem(out, false);
            }
        }
        return true;
    }

    private static int maxCraftableTimes(World world, State st) {
        // 根据当前配方需求与材料计算最多次数（简化：仅按每格需要>=1 来估计）
        java.util.ArrayList<ItemStack> list = new java.util.ArrayList<>(9);
        for (int i = 1; i <= 9; i++) list.add(st.slots[i].copy());
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, list);
        RecipeEntry<?> match = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).orElse(null);
        if (match == null || !(match.value() instanceof net.minecraft.recipe.CraftingRecipe crafting)) return 0;
        // 粗略算法：按非空输入格的最小计数
        int min = Integer.MAX_VALUE;
        for (int i = 1; i <= 9; i++) {
            if (!st.slots[i].isEmpty()) min = Math.min(min, st.slots[i].getCount());
        }
        return min == Integer.MAX_VALUE ? 0 : Math.max(0, min);
    }

    private static void consumeOnce(ServerPlayerEntity player, State st) {
        // 处理材料消耗与容器返还（如牛奶桶 -> 空桶）
        for (int i = 1; i <= 9; i++) {
            ItemStack s = st.slots[i];
            if (s.isEmpty()) continue;

            // 计算容器返还（物品定义的 recipe remainder）
            net.minecraft.item.Item remainderItem = s.getItem().getRecipeRemainder();

            // 消耗一个
            s.decrement(1);

            if (remainderItem != null) {
                ItemStack rem = new ItemStack(remainderItem);
                if (s.isEmpty()) {
                    // 槽位清空时，优先把返还物放回原位
                    st.slots[i] = rem;
                } else {
                    // 槽位仍有剩余（例如异构情况），将返还物放入背包，失败则丢出
                    if (!player.getInventory().insertStack(rem)) {
                        player.dropItem(rem, false);
                    }
                }
            } else if (s.isEmpty()) {
                st.slots[i] = ItemStack.EMPTY;
            }
        }
    }
}


