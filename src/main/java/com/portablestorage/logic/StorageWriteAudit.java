package com.portablestorage.logic;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StorageWriteAudit {
    private static final Map<String, Long> sourceCounters = new LinkedHashMap<>();
    private static long totalWrites = 0;
    private static String lastSource = "none";
    private static String lastDecision = "none";

    private StorageWriteAudit() {
    }

    public static synchronized void record(String source, String decision) {
        String safeSource = source == null || source.isBlank() ? "unknown" : source;
        totalWrites++;
        lastSource = safeSource;
        lastDecision = decision;
        sourceCounters.put(safeSource, sourceCounters.getOrDefault(safeSource, 0L) + 1);
    }

    public static synchronized long getTotalWrites() {
        return totalWrites;
    }

    public static synchronized String getLastSource() {
        return lastSource;
    }

    public static synchronized String getLastDecision() {
        return lastDecision;
    }

    public static synchronized Map<String, Long> snapshotBySource() {
        return new LinkedHashMap<>(sourceCounters);
    }
}
