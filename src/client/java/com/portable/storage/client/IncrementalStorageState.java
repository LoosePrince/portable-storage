package com.portable.storage.client;

import com.portable.storage.PortableStorage;
import com.portable.storage.net.payload.IncrementalStorageSyncS2CPayload;
import com.portable.storage.net.payload.SyncAckC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.collection.DefaultedList;
import com.mojang.serialization.DynamicOps;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端增量仓库状态管理
 * 处理增量同步数据并维护本地状态
 */
public class IncrementalStorageState {
    private static final Map<Integer, ItemStack> displaySlots = new HashMap<>();
    private static final Map<Integer, Long> slotCounts = new HashMap<>();
    private static final Map<Integer, Long> slotTimestamps = new HashMap<>();
    private static int capacity = 54;
    private static long lastSyncId = -1;
    
    /**
     * 处理增量同步数据
     */
    public static void handleIncrementalSync(IncrementalStorageSyncS2CPayload payload, RegistryWrapper.WrapperLookup lookup) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            sendAck(payload.syncId(), false);
            return;
        }
        
        try {
            if (payload.isFullSync()) {
                // 全量同步：清空现有状态
                clearState();
                PortableStorage.LOGGER.debug("Received full sync with {} changes", payload.changes().size());
            } else {
                PortableStorage.LOGGER.debug("Received incremental sync with {} changes", payload.changes().size());
            }
            
            // 处理每个变化
            for (IncrementalStorageSyncS2CPayload.StorageChange change : payload.changes()) {
                handleChange(change, lookup);
            }
            
            // 更新容量
            capacity = Math.max(capacity, displaySlots.size());
            
            // 发送确认
            sendAck(payload.syncId(), true);
            lastSyncId = payload.syncId();
            
            PortableStorage.LOGGER.debug("Processed sync successfully, current capacity: {}", capacity);
            
        } catch (Exception e) {
            PortableStorage.LOGGER.error("Failed to handle incremental sync", e);
            sendAck(payload.syncId(), false);
        }
    }
    
    /**
     * 处理单个变化
     */
    private static void handleChange(IncrementalStorageSyncS2CPayload.StorageChange change, RegistryWrapper.WrapperLookup lookup) {
        int slotIndex = change.slotIndex();
        
        switch (change.type()) {
            case ADD, UPDATE -> {
                NbtCompound data = change.data();
                ItemStack stack = ItemStack.EMPTY;
                if (data.contains("item")) {
                    // 使用CODEC反序列化ItemStack
                    final DynamicOps<net.minecraft.nbt.NbtElement> ops =
                        (lookup != null) ? RegistryOps.of(NbtOps.INSTANCE, lookup) : NbtOps.INSTANCE;
                    var parse = ItemStack.CODEC.parse(ops, data.get("item"));
                    stack = parse.result().orElse(ItemStack.EMPTY);
                }
                long count = data.getLong("count");
                long timestamp = data.getLong("timestamp");
                
                if (!stack.isEmpty() && count > 0) {
                    displaySlots.put(slotIndex, stack);
                    slotCounts.put(slotIndex, count);
                    slotTimestamps.put(slotIndex, timestamp);
                }
            }
            case REMOVE -> {
                displaySlots.remove(slotIndex);
                slotCounts.remove(slotIndex);
                slotTimestamps.remove(slotIndex);
            }
            case CLEAR -> {
                clearState();
            }
        }
    }
    
    /**
     * 清空状态
     */
    private static void clearState() {
        displaySlots.clear();
        slotCounts.clear();
        slotTimestamps.clear();
    }
    
    /**
     * 发送同步确认
     */
    private static void sendAck(long syncId, boolean success) {
        ClientPlayNetworking.send(new SyncAckC2SPayload(syncId, success));
    }
    
    /**
     * 获取显示用的物品列表
     */
    public static DefaultedList<ItemStack> getDisplayList() {
        DefaultedList<ItemStack> list = DefaultedList.ofSize(capacity, ItemStack.EMPTY);
        for (Map.Entry<Integer, ItemStack> entry : displaySlots.entrySet()) {
            int index = entry.getKey();
            if (index >= 0 && index < capacity) {
                list.set(index, entry.getValue());
            }
        }
        return list;
    }
    
    /**
     * 获取指定槽位的数量
     */
    public static long getCount(int slotIndex) {
        return slotCounts.getOrDefault(slotIndex, 0L);
    }
    
    /**
     * 获取指定槽位的时间戳
     */
    public static long getTimestamp(int slotIndex) {
        return slotTimestamps.getOrDefault(slotIndex, 0L);
    }
    
    /**
     * 获取容量
     */
    public static int getCapacity() {
        return capacity;
    }
    
    /**
     * 检查是否有数据
     */
    public static boolean hasData() {
        return !displaySlots.isEmpty();
    }
    
    /**
     * 获取最后同步ID
     */
    public static long getLastSyncId() {
        return lastSyncId;
    }
}
