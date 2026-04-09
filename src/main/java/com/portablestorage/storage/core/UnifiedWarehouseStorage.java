package com.portablestorage.storage.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.portablestorage.storage.key.WarehouseStackKey;

public final class UnifiedWarehouseStorage {
    private final Map<WarehouseStackKey, Long> amountMap = new LinkedHashMap<>();
    private final Consumer<Void> onChanged;
    private final Map<String, Set<WarehouseStackKey>> typeBuckets = new LinkedHashMap<>();
    private final Map<WarehouseStackKey, Integer> slotIndex = new LinkedHashMap<>();
    private long nextSlot = 0;
    private long lastModified = 0;
    private boolean dirty;

    public UnifiedWarehouseStorage(Consumer<Void> onChanged) {
        this.onChanged = onChanged;
    }

    public long insert(WarehouseStackKey key, long amount, boolean simulate) {
        if (key == null || amount <= 0) {
            return 0;
        }
        if (simulate) {
            return amount;
        }

        amountMap.put(key, amountMap.getOrDefault(key, 0L) + amount);
        rebuildIndexes();
        onChanged();
        return amount;
    }

    public long extract(WarehouseStackKey key, long amount, boolean simulate) {
        if (key == null || amount <= 0) {
            return 0;
        }
        long current = amountMap.getOrDefault(key, 0L);
        long extracted = Math.min(current, amount);
        if (!simulate && extracted > 0) {
            long next = current - extracted;
            if (next > 0) {
                amountMap.put(key, next);
            } else {
                amountMap.remove(key);
            }
            rebuildIndexes();
            onChanged();
        }
        return extracted;
    }

    public long getAmount(WarehouseStackKey key) {
        return amountMap.getOrDefault(key, 0L);
    }

    public Map<WarehouseStackKey, Long> snapshot() {
        return new LinkedHashMap<>(amountMap);
    }

    public void replaceAll(Map<WarehouseStackKey, Long> newData) {
        amountMap.clear();
        amountMap.putAll(newData);
        rebuildIndexes();
        onChanged();
    }

    public void clear() {
        if (amountMap.isEmpty()) {
            return;
        }
        amountMap.clear();
        rebuildIndexes();
        onChanged();
    }

    public boolean isEmpty() {
        return amountMap.isEmpty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public long getLastModified() {
        return lastModified;
    }

    public Map<String, Set<WarehouseStackKey>> getTypeBucketsSnapshot() {
        Map<String, Set<WarehouseStackKey>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<WarehouseStackKey>> entry : typeBuckets.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    public Map<WarehouseStackKey, Integer> getSlotIndexSnapshot() {
        return new LinkedHashMap<>(slotIndex);
    }

    public void clearDirty() {
        this.dirty = false;
    }

    private void rebuildIndexes() {
        Map<WarehouseStackKey, Integer> previousSlotMap = new LinkedHashMap<>(slotIndex);
        typeBuckets.clear();
        slotIndex.clear();
        for (WarehouseStackKey key : amountMap.keySet()) {
            typeBuckets.computeIfAbsent(key.typeId(), __ -> new LinkedHashSet<>()).add(key);
            Integer existed = previousSlotMap.get(key);
            if (existed != null) {
                slotIndex.put(key, existed);
            } else {
                slotIndex.put(key, (int) nextSlot++);
            }
        }
    }

    private void onChanged() {
        this.dirty = true;
        this.lastModified++;
        if (onChanged != null) {
            onChanged.accept(null);
        }
    }
}
