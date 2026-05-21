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

        boolean existed = amountMap.containsKey(key);
        amountMap.put(key, amountMap.getOrDefault(key, 0L) + amount);
        if (!existed) {
            addIndex(key);
        }
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
                removeIndex(key);
            }
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

    public int getTypeCount(String typeId) {
        Set<WarehouseStackKey> bucket = typeBuckets.get(typeId);
        return bucket == null ? 0 : bucket.size();
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
            addIndex(key, previousSlotMap.get(key));
        }
    }

    private void addIndex(WarehouseStackKey key) {
        addIndex(key, slotIndex.get(key));
    }

    private void addIndex(WarehouseStackKey key, Integer preferredSlot) {
        typeBuckets.computeIfAbsent(key.typeId(), __ -> new LinkedHashSet<>()).add(key);
        if (preferredSlot != null) {
            slotIndex.put(key, preferredSlot);
        } else {
            slotIndex.put(key, (int) nextSlot++);
        }
    }

    private void removeIndex(WarehouseStackKey key) {
        Set<WarehouseStackKey> bucket = typeBuckets.get(key.typeId());
        if (bucket != null) {
            bucket.remove(key);
            if (bucket.isEmpty()) {
                typeBuckets.remove(key.typeId());
            }
        }
        slotIndex.remove(key);
    }

    private void onChanged() {
        this.dirty = true;
        this.lastModified++;
        if (onChanged != null) {
            onChanged.accept(null);
        }
    }
}
